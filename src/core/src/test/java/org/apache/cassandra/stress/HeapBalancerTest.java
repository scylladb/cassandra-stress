package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeapBalancerTest {
    private static final long MIB = 1L << 20;
    private static final long GIB = 1L << 30;

    @TempDir
    Path dir;

    @Test
    void givesEachProcessAnEqualPartOfThreeQuartersOfThePool() {
        assertEquals(9 * GIB, HeapBalancer.softMaxHeap(12 * GIB, 9 * GIB, 27 * GIB, 4, 40 * GIB));
        assertEquals(36 * GIB, HeapBalancer.softMaxHeap(48 * GIB, 0, 0, 1, 40 * GIB));
    }

    /** A new process grows only into the memory that is free now, so it waits for the others. */
    @Test
    void growsOnlyIntoTheFreeMemory() {
        assertEquals(GIB + 384 * MIB, HeapBalancer.softMaxHeap(GIB, GIB, 36 * GIB, 2, 40 * GIB));
    }

    @Test
    void staysBetweenTheMinimumAndTheMaximumHeap() {
        assertEquals(HeapBalancer.MIN_SOFT_MAX_HEAP, HeapBalancer.softMaxHeap(256 * MIB, 0, 0, 8, 4 * GIB));
        assertEquals(2 * GIB, HeapBalancer.softMaxHeap(48 * GIB, 0, 0, 1, 2 * GIB));
    }

    @Test
    void roundsDownToSteps() {
        assertEquals(752 * MIB, HeapBalancer.softMaxHeap(1003 * MIB, 0, 0, 1, 4 * GIB));
    }

    @Test
    void readsTheNumberOfAField() throws IOException {
        Path meminfo =
                Files.writeString(dir.resolve("meminfo"), "MemTotal:       16384000 kB\nMemAvailable:    8192000 kB\n");
        assertEquals(
                8192000,
                HeapBalancer.SystemMemory.field(meminfo, "MemAvailable:").orElseThrow());
        assertTrue(HeapBalancer.SystemMemory.field(meminfo, "Missing:").isEmpty());
    }

    /** A parent cgroup with a lower limit sets the budget, also when the cgroup of the process has none. */
    @Test
    void readsTheLimitsOfTheParentCgroups() throws IOException {
        Path root = dir.resolve("cgroup");
        Path parent = cgroup(root.resolve("system.slice"), "memory.max", 2 * GIB, "memory.current", "inactive_file");
        Path leaf = Files.createDirectories(parent.resolve("stress.scope"));
        Files.writeString(leaf.resolve("memory.max"), "max\n");
        Path proc = Files.writeString(dir.resolve("proc-cgroup"), "0::/system.slice/stress.scope\n");

        List<HeapBalancer.SystemMemory.Limit> limits =
                HeapBalancer.SystemMemory.limitedCgroups(meminfo(16 * GIB), proc, root);
        assertEquals(
                List.of(parent),
                limits.stream().map(HeapBalancer.SystemMemory.Limit::directory).toList());
        assertEquals(GIB + 256 * MIB, limits.getFirst().free().orElseThrow());
    }

    @Test
    void choosesTheCgroupWithTheLowestLimit() throws IOException {
        Path root = dir.resolve("cgroup");
        Path parent = cgroup(root.resolve("a"), "memory.max", GIB, "memory.current", "inactive_file");
        cgroup(parent.resolve("b"), "memory.max", 4 * GIB, "memory.current", "inactive_file");
        Path proc = Files.writeString(dir.resolve("proc-cgroup"), "0::/a/b\n");

        List<HeapBalancer.SystemMemory.Limit> limits =
                HeapBalancer.SystemMemory.limitedCgroups(meminfo(16 * GIB), proc, root);
        assertEquals(2, limits.size());
        assertEquals(
                parent,
                HeapBalancer.SystemMemory.lowestLimit(limits).orElseThrow().directory());
    }

    /** On cgroup v1, the memory cgroup of the process is below the mount of the memory hierarchy. */
    @Test
    void readsTheNestedMemoryCgroupOfCgroupV1() throws IOException {
        Path root = dir.resolve("cgroup");
        Path nested = cgroup(
                root.resolve("memory/docker/abc"),
                "memory.limit_in_bytes",
                GIB,
                "memory.usage_in_bytes",
                "total_inactive_file");
        Files.writeString(root.resolve("memory/memory.limit_in_bytes"), "9223372036854771712\n");
        Path proc = Files.writeString(dir.resolve("proc-cgroup"), "5:cpu,cpuacct:/docker/abc\n4:memory:/docker/abc\n");

        List<HeapBalancer.SystemMemory.Limit> limits =
                HeapBalancer.SystemMemory.limitedCgroups(meminfo(16 * GIB), proc, root);
        assertEquals(
                List.of(nested),
                limits.stream().map(HeapBalancer.SystemMemory.Limit::directory).toList());
    }

    /** A container with its own cgroup namespace on cgroup v1 has its limit at the mount. */
    @Test
    void readsTheMountWhenThePathOfCgroupV1IsNotBelowIt() throws IOException {
        Path root = dir.resolve("cgroup");
        Path mount = cgroup(
                root.resolve("memory"), "memory.limit_in_bytes", GIB, "memory.usage_in_bytes", "total_inactive_file");
        Path proc = Files.writeString(dir.resolve("proc-cgroup"), "4:memory:/docker/abc\n");

        List<HeapBalancer.SystemMemory.Limit> limits =
                HeapBalancer.SystemMemory.limitedCgroups(meminfo(16 * GIB), proc, root);
        assertEquals(
                List.of(mount),
                limits.stream().map(HeapBalancer.SystemMemory.Limit::directory).toList());
    }

    /** A cgroup with {@code limit} bytes, 1 GiB in use and 256 MiB of page cache that the kernel can drop. */
    private static Path cgroup(Path directory, String limitFile, long limit, String usage, String inactive)
            throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(limitFile), limit + "\n");
        Files.writeString(directory.resolve(usage), GIB + "\n");
        Files.writeString(directory.resolve("memory.stat"), inactive + " " + 256 * MIB + "\n");
        return directory;
    }

    private Path meminfo(long total) throws IOException {
        return Files.writeString(dir.resolve("meminfo"), "MemTotal: " + (total >> 10) + " kB\n");
    }

    /** Two balancers in one range see each other, and a program that does not greet does not count. */
    @Test
    void countsTheStressProcessesThatAnswerOnTheRange() throws IOException {
        int first = freeRange(4);
        HeapBalancer one = new HeapBalancer(first, 4, "machine");
        HeapBalancer two = new HeapBalancer(first, 4, "machine");
        try (ServerSocket other = new ServerSocket(first + 2, 50, InetAddress.getLoopbackAddress())) {
            Thread thread = new Thread(() -> {
                try (Socket socket = other.accept();
                        OutputStream out = socket.getOutputStream()) {
                    out.write("HTTP/1.1 400\n".getBytes(StandardCharsets.US_ASCII));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            thread.setDaemon(true);
            thread.start();
            one.listen();
            two.listen();
            HeapBalancer.Peers peers = one.peers();
            assertEquals(1, peers.count());
            assertTrue(peers.committed() > 0);
        } finally {
            one.close();
            two.close();
        }
        assertEquals(0, new HeapBalancer(first, 4, "machine").peers().count());
    }

    /** A process counts only the processes with its memory budget. */
    @Test
    void countsOnlyTheProcessesWithTheSameBudget() throws IOException {
        int first = freeRange(2);
        HeapBalancer machine = new HeapBalancer(first, 2, "machine");
        HeapBalancer container = new HeapBalancer(first, 2, "cgroup-42");
        try {
            machine.listen();
            container.listen();
            assertEquals(0, machine.peers().count());
            assertEquals(0, container.peers().count());
        } finally {
            machine.close();
            container.close();
        }
    }

    @Test
    void readsTheUserOfEachListeningPort() throws IOException {
        Path tcp = Files.writeString(dir.resolve("tcp"), """
                  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                   0: 0100007F:9984 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 1 1
                   1: 0100007F:9985 0100007F:D2A0 01 00000000:00000000 00:00000000 00000000     0        0 2 1
                """);
        assertEquals(Map.of(39300, 1000L), HeapBalancer.SystemMemory.listeners(tcp));
    }

    /** The first port of {@code size} loopback ports that no program uses. */
    private static int freeRange(int size) throws IOException {
        for (int first = 40000; first < 60000; first += size) {
            if (rangeIsFree(first, size)) {
                return first;
            }
        }
        throw new IOException("No free range of " + size + " ports");
    }

    private static boolean rangeIsFree(int first, int size) {
        for (int port = first; port < first + size; port++) {
            try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
                socket.getLocalPort();
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }
}
