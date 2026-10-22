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
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import javax.management.JMException;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shares the memory of a machine among the stress processes on it. Each process listens on the first
 * free loopback port of a fixed range and answers each connection with its committed heap. Every two
 * seconds, the balancer connects to the other ports of the range, so it knows the live processes and
 * their heaps. The containers of SCT use the network of the host, so they share the loopback ports.
 * The pool is the available memory plus the committed heaps of the live processes. On ZGC, the
 * balancer sets the soft maximum heap to the fair share of this process: 3/4 of the pool divided by
 * the number of processes. A process grows only into memory that is free now, so the soft maximum
 * heap is also at most its committed heap plus its part of 3/4 of the available memory. A new process
 * thus waits for the others to return their memory. When the soft maximum heap falls below the
 * committed heap, the balancer starts a collection at once and checks every 250 milliseconds. It also
 * checks every 250 milliseconds in the first 10 seconds, so processes that start together see each
 * other before their heaps grow. ZGC then
 * keeps the heap below the soft maximum heap and returns the memory above it to the system.
 */
final class HeapBalancer implements Runnable {
    static final int FIRST_PORT = 39300;
    static final int PORTS = 64;
    static final String GREETING = "cassandra-stress-heap ";
    static final long MIN_SOFT_MAX_HEAP = 64L << 20;
    private static final int TIMEOUT_MILLIS = 200;
    private static final long PERIOD_MILLIS = 2000;
    private static final long FAST_PERIOD_MILLIS = 250;
    private static final long STARTUP_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final long STEP = 16L << 20;
    private static final Logger LOGGER = LoggerFactory.getLogger(HeapBalancer.class);

    private final int firstPort;
    private final int ports;
    private final boolean setsSoftMaxHeap;
    private final InetAddress loopback = InetAddress.getLoopbackAddress();
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final long maxHeap = Runtime.getRuntime().maxMemory();
    private ServerSocket server;
    private long softMaxHeap = -1;
    private long lastCollection;
    private volatile boolean shrinking;
    private final long started = System.nanoTime();

    HeapBalancer(int firstPort, int ports, boolean setsSoftMaxHeap) {
        this.firstPort = firstPort;
        this.ports = ports;
        this.setsSoftMaxHeap = setsSoftMaxHeap;
    }

    /**
     * Starts the balancer on a machine that shows its memory, as Linux does. The first balance runs
     * before the workload, so the heap grows to the share of this process only.
     */
    static void start() {
        if (SystemMemory.available().isEmpty()) {
            return;
        }
        boolean zgc = runsOnZgc();
        HeapBalancer balancer = new HeapBalancer(FIRST_PORT, PORTS, zgc);
        balancer.listen();
        // G1 has no soft maximum heap, so a process on G1 only shows its heap to the others.
        if (!zgc) {
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

    /** Listens on the first free port of the range. With no free port, the others do not see this process. */
    void listen() {
        for (int port = firstPort; port < firstPort + ports; port++) {
            try {
                server = new ServerSocket(port, 50, loopback);
            } catch (IOException e) {
                continue;
            }
            Thread thread = new Thread(this::answer, "heap-balancer-server");
            thread.setDaemon(true);
            thread.start();
            return;
        }
        LOGGER.debug("All the ports from {} to {} are in use", firstPort, firstPort + ports - 1);
    }

    private void answer() {
        while (!server.isClosed()) {
            try (Socket socket = server.accept();
                    OutputStream out = socket.getOutputStream()) {
                out.write((GREETING + committedHeap() + "\n").getBytes(US_ASCII));
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

    private void balance() {
        OptionalLong available = SystemMemory.available();
        if (available.isEmpty()) {
            return;
        }
        Peers peers = peers();
        long committed = committedHeap();
        long target = softMaxHeap(available.getAsLong(), committed, peers.committed(), peers.count() + 1, maxHeap);
        if (!setsSoftMaxHeap) {
            return;
        }
        if (target != softMaxHeap) {
            ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                    .setVMOption("SoftMaxHeapSize", Long.toString(target));
            softMaxHeap = target;
            LOGGER.debug("Soft maximum heap {} MiB with {} stress processes", target >> 20, peers.count() + 1);
        }
        shrinking = committed > target + STEP;
        long now = System.nanoTime();
        if (shrinking && now - lastCollection > TimeUnit.SECONDS.toNanos(1)) {
            lastCollection = now;
            collect();
        }
    }

    /** Starts a collection, so the heap falls below the soft maximum heap without waiting for ZGC. */
    private static void collect() {
        try {
            ManagementFactory.getPlatformMBeanServer()
                    .invoke(
                            new ObjectName("com.sun.management:type=DiagnosticCommand"),
                            "gcRun",
                            new Object[] {null},
                            new String[] {String[].class.getName()});
        } catch (JMException e) {
            LOGGER.debug("Cannot start a collection", e);
        }
    }

    /** The number of the other stress processes and the sum of their committed heaps. */
    record Peers(int count, long committed) {}

    /** The other stress processes that answer on the ports of the range. */
    Peers peers() {
        int count = 0;
        long committed = 0;
        int own = server == null ? -1 : server.getLocalPort();
        for (int port = firstPort; port < firstPort + ports; port++) {
            if (port == own) {
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

    /** The committed heap of the stress process on {@code port}, or nothing when another program has it. */
    private OptionalLong ask(int port) {
        try (Socket socket = connected(port);
                InputStream in = socket.getInputStream()) {
            String line = new String(in.readNBytes(64), US_ASCII).strip();
            if (!line.startsWith(GREETING)) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Long.parseLong(line.substring(GREETING.length())));
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

    void close() throws IOException {
        if (server != null) {
            server.close();
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

    /** The memory that a new process can take, as the launcher reads it. */
    static final class SystemMemory {
        private SystemMemory() {}

        /**
         * The available memory of the machine, or less when the cgroup of this process has a lower
         * limit. The page cache that the kernel can drop counts as available.
         */
        static OptionalLong available() {
            OptionalLong machine = field(Path.of("/proc/meminfo"), "MemAvailable:").stream()
                    .map(kib -> kib << 10)
                    .findFirst();
            if (machine.isEmpty()) {
                return machine;
            }
            OptionalLong cgroup = cgroupFree();
            return cgroup.isPresent() && cgroup.getAsLong() < machine.getAsLong() ? cgroup : machine;
        }

        private static OptionalLong cgroupFree() {
            try {
                Optional<String> path = Files.readAllLines(Path.of("/proc/self/cgroup")).stream()
                        .filter(line -> line.startsWith("0::"))
                        .map(line -> line.substring(3))
                        .findFirst();
                if (path.isEmpty()) {
                    return OptionalLong.empty();
                }
                Path dir = Path.of("/sys/fs/cgroup" + path.get().replaceAll("/$", ""));
                String limit = Files.readString(dir.resolve("memory.max")).strip();
                if ("max".equals(limit)) {
                    return OptionalLong.empty();
                }
                long usage = Long.parseLong(
                        Files.readString(dir.resolve("memory.current")).strip());
                long inactive =
                        field(dir.resolve("memory.stat"), "inactive_file").orElse(0);
                return OptionalLong.of(Math.max(0, Long.parseLong(limit) - usage + inactive));
            } catch (IOException | NumberFormatException e) {
                return OptionalLong.empty();
            }
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
