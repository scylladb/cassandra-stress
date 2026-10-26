// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shares the memory of a machine among the stress processes on it. Each process listens on the first
 * free loopback port of a fixed range. It answers each connection with its committed heap and its
 * memory budget: the machine, or the cgroup with the lowest memory limit. Every two seconds, the balancer
 * connects to the ports of the range that a process of the same user listens on. Thus it knows the
 * live processes with the same budget and their heaps. The containers of SCT use the network of the
 * host, so they share the loopback ports.
 *
 * <p>The pool is the free memory plus the committed heaps of the live processes. The fair share of a
 * process is 3/4 of the pool divided by the number of processes. A process grows only into memory that
 * is free now, so on ZGC the balancer sets the soft maximum heap to the smaller of the fair share and
 * the committed heap plus its part of 3/4 of the free memory. A new process thus waits for the others
 * to return their memory. When the committed heap is above the soft maximum heap and the initial
 * heap, the balancer starts up to three collections, one each second, and checks every 250
 * milliseconds. It also checks every 250 milliseconds in the first 10 seconds, so processes that start
 * together see each other before their heaps grow.
 *
 * <p>On G1, and with {@code -Dcassandra.stress.heap.balance=false} from a heap in JVM_OPTS, the
 * balancer only reports the heap of the process to the others.
 */
final class HeapBalancer implements Runnable {
    static final int FIRST_PORT = 39300;
    static final int PORTS = 64;
    static final String GREETING = "cassandra-stress-heap ";
    static final String BALANCE_PROPERTY = "cassandra.stress.heap.balance";
    static final long MIN_SOFT_MAX_HEAP = 64L << 20;
    private static final int TIMEOUT_MILLIS = 200;
    private static final long PERIOD_MILLIS = 2000;
    private static final long FAST_PERIOD_MILLIS = 250;
    private static final long STARTUP_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final long COLLECTION_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final int COLLECTIONS = 3;
    private static final long STEP = 16L << 20;
    private static final String LISTEN = "0A";
    private static final Logger LOGGER = LoggerFactory.getLogger(HeapBalancer.class);

    private final int firstPort;
    private final int ports;
    private final String budget;
    private final InetAddress loopback = InetAddress.getLoopbackAddress();
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final long maxHeap = Runtime.getRuntime().maxMemory();
    private final long started = System.nanoTime();
    private ServerSocket server;
    private Thread answering;
    private long softMaxHeap = -1;
    private long lastCollection;
    private int collections;
    private volatile boolean shrinking;

    HeapBalancer(int firstPort, int ports, String budget) {
        this.firstPort = firstPort;
        this.ports = ports;
        this.budget = budget;
    }

    /**
     * Starts the balancer on a machine that shows its memory, as Linux does. On ZGC, the first balance
     * runs before the workload, so the heap grows to the share of this process.
     */
    static void start() {
        if (SystemMemory.available().isEmpty()) {
            return;
        }
        HeapBalancer balancer = new HeapBalancer(FIRST_PORT, PORTS, SystemMemory.budget());
        balancer.listen();
        if (!runsOnZgc() || !Boolean.parseBoolean(System.getProperty(BALANCE_PROPERTY, "true"))) {
            return;
        }
        balancer.balanceOnce();
        Thread thread = new Thread(balancer, "heap-balancer");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean runsOnZgc() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName)
                .anyMatch(name -> name.startsWith("ZGC"));
    }

    /** Listens on the first free port of the range and answers on it until the process stops. */
    void listen() {
        for (int port = firstPort; port < firstPort + ports; port++) {
            try {
                server = new ServerSocket(port, 50, loopback);
            } catch (IOException e) {
                continue;
            }
            answering = new Thread(this::answer, "heap-balancer-server");
            answering.setDaemon(true);
            answering.start();
            return;
        }
        LOGGER.debug("All the ports from {} to {} are in use", firstPort, firstPort + ports - 1);
    }

    private void answer() {
        while (!server.isClosed()) {
            try (Socket socket = server.accept();
                    OutputStream out = socket.getOutputStream()) {
                out.write((GREETING + committedHeap() + " " + budget + "\n").getBytes(US_ASCII));
            } catch (IOException e) {
                LOGGER.debug("Cannot answer a stress process", e);
            }
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                boolean starting = System.nanoTime() - started < STARTUP_NANOS;
                Thread.sleep(shrinking || starting ? FAST_PERIOD_MILLIS : PERIOD_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            balanceOnce();
        }
    }

    private void balanceOnce() {
        try {
            balance();
        } catch (RuntimeException e) {
            LOGGER.debug("Cannot balance the heap with the other stress processes", e);
        }
    }

    // The collection lets ZGC return the heap above a lower soft maximum heap.
    @SuppressWarnings("PMD.DoNotCallGarbageCollectionExplicitly")
    private void balance() {
        OptionalLong available = SystemMemory.available();
        if (available.isEmpty()) {
            return;
        }
        Peers peers = peers();
        long committed = committedHeap();
        long target = softMaxHeap(available.getAsLong(), committed, peers.committed(), peers.count() + 1, maxHeap);
        if (target != softMaxHeap) {
            ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                    .setVMOption("SoftMaxHeapSize", Long.toString(target));
            softMaxHeap = target;
            collections = 0;
            LOGGER.debug("Soft maximum heap {} MiB with {} stress processes", target >> 20, peers.count() + 1);
        }
        // ZGC keeps at least the initial heap, and a collection frees only the garbage. So the balancer
        // stops after a few collections that leave the heap above the soft maximum heap.
        long floor = Math.max(target, memory.getHeapMemoryUsage().getInit());
        shrinking = committed > floor + STEP && collections < COLLECTIONS;
        long now = System.nanoTime();
        if (shrinking && now - lastCollection > COLLECTION_NANOS) {
            lastCollection = now;
            collections++;
            System.gc();
        }
    }

    /** The number of the other stress processes with the same budget and the sum of their committed heaps. */
    record Peers(int count, long committed) {}

    /** The other stress processes of this user and budget that answer on the ports of the range. */
    Peers peers() {
        Map<Integer, Long> listeners = SystemMemory.loopbackListeners();
        OptionalLong uid = SystemMemory.uid();
        int count = 0;
        long committed = 0;
        int own = server == null ? -1 : server.getLocalPort();
        for (int port = firstPort; port < firstPort + ports; port++) {
            if (port == own) {
                continue;
            }
            Long owner = listeners.get(port);
            if (owner != null && uid.isPresent() && owner != uid.getAsLong()) {
                continue;
            }
            OptionalLong heap = ask(port);
            if (heap.isPresent()) {
                count++;
                committed += heap.getAsLong();
            }
        }
        return new Peers(count, committed);
    }

    /** The committed heap of the stress process on {@code port} when it has the budget of this process. */
    private OptionalLong ask(int port) {
        try (Socket socket = connected(port);
                InputStream in = socket.getInputStream()) {
            String[] parts = new String(in.readNBytes(128), US_ASCII).strip().split(" ");
            if (parts.length != 3 || !GREETING.equals(parts[0] + " ") || !budget.equals(parts[2])) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Long.parseLong(parts[1]));
        } catch (IOException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private Socket connected(int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(loopback, port), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private long committedHeap() {
        return memory.getHeapMemoryUsage().getCommitted();
    }

    /**
     * Closes the port and waits for the thread that answers on it. The JDK closes the port only when
     * that thread leaves accept, and until then a connection can still get an answer.
     */
    void close() throws IOException {
        if (server == null) {
            return;
        }
        server.close();
        try {
            answering.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The soft maximum heap of one of {@code processes} processes, with {@code available} bytes free, a
     * committed heap of {@code committed} bytes and {@code others} bytes in the heaps of the others.
     */
    static long softMaxHeap(long available, long committed, long others, int processes, long maxHeap) {
        int count = Math.max(processes, 1);
        long fair = (available + committed + others) / 4 * 3 / count;
        long free = committed + available / 4 * 3 / count;
        // Steps of 16 MiB, so a small change of the memory does not start a collection.
        long share = Math.min(fair, free) / STEP * STEP;
        return Math.min(Math.max(share, MIN_SOFT_MAX_HEAP), maxHeap);
    }

    /** The memory of this process as Linux shows it, read the same way as the launcher reads it. */
    static final class SystemMemory {
        private static final Path MEMINFO = Path.of("/proc/meminfo");
        private static final Path PROC_CGROUP = Path.of("/proc/self/cgroup");
        private static final Path CGROUP = Path.of("/sys/fs/cgroup");

        private SystemMemory() {}

        /**
         * The available memory of the machine, or less when a cgroup of this process has a lower limit.
         * The page cache that the kernel can drop counts as available.
         */
        static OptionalLong available() {
            OptionalLong machine = field(MEMINFO, "MemAvailable:").stream()
                    .map(kib -> kib << 10)
                    .findFirst();
            if (machine.isEmpty()) {
                return machine;
            }
            long available = machine.getAsLong();
            for (Limit limit : limitedCgroups(MEMINFO, PROC_CGROUP, CGROUP)) {
                available = Math.min(available, limit.free().orElse(available));
            }
            return OptionalLong.of(available);
        }

        /**
         * The budget of this process: {@code cgroup-<inode>} for the cgroup with the lowest memory limit,
         * or {@code machine}. Processes under that cgroup see the same inode.
         */
        static String budget() {
            return lowestLimit(limitedCgroups(MEMINFO, PROC_CGROUP, CGROUP))
                    .flatMap(limit -> inode(limit.directory()))
                    .map(inode -> "cgroup-" + inode)
                    .orElse("machine");
        }

        static Optional<Limit> lowestLimit(List<Limit> limits) {
            return limits.stream().min(Comparator.comparingLong(Limit::limit));
        }

        /** The memory limit of a cgroup, with the names of its files for cgroup v2 or cgroup v1. */
        record Limit(Path directory, long limit, String usage, String inactive) {
            Optional<Long> free() {
                try {
                    long used = Long.parseLong(
                            Files.readString(directory.resolve(usage)).strip());
                    long dropped =
                            field(directory.resolve("memory.stat"), inactive).orElse(0);
                    return Optional.of(Math.max(0, limit - used + dropped));
                } catch (IOException | NumberFormatException e) {
                    return Optional.empty();
                }
            }
        }

        /**
         * The cgroups of this process with a memory limit below the memory of the machine, from its own
         * cgroup up to the root. A parent cgroup can have a lower limit than the cgroup of the process.
         * Uses cgroup v2 when it controls the memory, and otherwise the memory hierarchy of cgroup v1.
         */
        static List<Limit> limitedCgroups(Path meminfo, Path procCgroup, Path cgroupRoot) {
            OptionalLong total = field(meminfo, "MemTotal:");
            List<String> lines;
            try {
                lines = Files.readAllLines(procCgroup);
            } catch (IOException e) {
                return List.of();
            }
            List<Limit> limits = cgroupV2(lines, cgroupRoot);
            if (limits.isEmpty()) {
                limits = cgroupV1(lines, cgroupRoot.resolve("memory"));
            }
            return limits.stream()
                    .filter(l -> total.isEmpty() || l.limit() < total.getAsLong() << 10)
                    .toList();
        }

        private static List<Limit> cgroupV2(List<String> lines, Path root) {
            return lines.stream()
                    .filter(line -> line.startsWith("0::"))
                    .findFirst()
                    .map(line -> ancestors(root, line.substring(3), "memory.max", "memory.current", "inactive_file"))
                    .orElse(List.of());
        }

        private static List<Limit> cgroupV1(List<String> lines, Path mount) {
            return lines.stream()
                    .map(line -> line.split(":", 3))
                    .filter(parts -> parts.length == 3
                            && Arrays.asList(parts[1].split(",")).contains("memory"))
                    .findFirst()
                    .map(parts -> ancestors(
                            mount, parts[2], "memory.limit_in_bytes", "memory.usage_in_bytes", "total_inactive_file"))
                    .orElse(List.of());
        }

        /**
         * The limits of the cgroup at {@code path} below {@code root} and of each of its parents. A container
         * with its own cgroup namespace on cgroup v1 mounts its cgroup at {@code root}, so its path in
         * /proc/self/cgroup is not below the mount.
         */
        private static List<Limit> ancestors(Path root, String path, String limitFile, String usage, String inactive) {
            Path directory = root.resolve(path.replaceAll("^/+", "")).normalize();
            if (!directory.startsWith(root) || !Files.isDirectory(directory)) {
                directory = root;
            }
            List<Limit> limits = new ArrayList<>();
            for (Path d = directory; d != null && d.startsWith(root); d = d.getParent()) {
                OptionalLong limit = limit(d.resolve(limitFile));
                if (limit.isPresent()) {
                    limits.add(new Limit(d, limit.getAsLong(), usage, inactive));
                }
            }
            return limits;
        }

        /** The number in a limit file, or nothing for "max" or a missing file, as at the root of cgroup v2. */
        private static OptionalLong limit(Path file) {
            try {
                String limit = Files.readString(file).strip();
                return "max".equals(limit) ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(limit));
            } catch (IOException | NumberFormatException e) {
                return OptionalLong.empty();
            }
        }

        private static Optional<Long> inode(Path path) {
            try {
                return Optional.of(((Number) Files.getAttribute(path, "unix:ino")).longValue());
            } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        /** The user ID of this process. */
        static OptionalLong uid() {
            return field(Path.of("/proc/self/status"), "Uid:");
        }

        /** The user ID of each process that listens on a loopback port, by port. */
        static Map<Integer, Long> loopbackListeners() {
            return listeners(Path.of("/proc/net/tcp"));
        }

        /** The user ID of each listening socket in {@code file}, in the format of /proc/net/tcp, by port. */
        static Map<Integer, Long> listeners(Path file) {
            Map<Integer, Long> owners = new HashMap<>();
            try {
                for (String line : Files.readAllLines(file)) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 8 || !LISTEN.equals(parts[3]) || !parts[1].contains(":")) {
                        continue;
                    }
                    String port = parts[1].substring(parts[1].indexOf(':') + 1);
                    owners.put(Integer.parseInt(port, 16), Long.parseLong(parts[7]));
                }
            } catch (IOException | NumberFormatException e) {
                return Map.of();
            }
            return owners;
        }

        /** The number after {@code name} on the line of {@code file} that starts with it. */
        static OptionalLong field(Path file, String name) {
            try {
                List<String> lines = Files.readAllLines(file);
                for (String line : lines) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2 && parts[0].equals(name)) {
                        return OptionalLong.of(Long.parseLong(parts[1]));
                    }
                }
            } catch (IOException | NumberFormatException e) {
                return OptionalLong.empty();
            }
            return OptionalLong.empty();
        }
    }
}
