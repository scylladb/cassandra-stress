package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Runs bin/cassandra-stress from the tree that ./gradlew installDist writes, on the module path. */
@Tag("distribution")
class DistributionIT {
    private static final Path HOME =
            Path.of(System.getProperty("stress.distribution", "build/install/cassandra-stress"));
    private static final String REPLICATION = "replication(strategy=NetworkTopologyStrategy,replication_factor=1)";
    private static final List<String> PREDEFINED = List.of(
            "cl=QUORUM",
            "n=10000",
            "-errors",
            "fail-fast",
            "-schema",
            REPLICATION,
            "-rate",
            "threads=4",
            "-pop",
            "seq=1..20971520",
            "-col",
            "n=FIXED(10) size=FIXED(512)",
            "-log",
            "interval=5");

    @TempDir
    Path dir;

    static Stream<Arguments> workloads() {
        return Stream.of("native", "4x")
                .flatMap(driver -> Stream.of(
                        Arguments.of(driver, "write", List.of(predefined("write"))),
                        Arguments.of(driver, "write and read", List.of(predefined("write"), predefined("read"))),
                        Arguments.of(driver, "cqlstress-example", List.of(profile("cqlstress-example.yaml"))),
                        Arguments.of(
                                driver,
                                "cqlstress-counter-example",
                                List.of(profile("cqlstress-counter-example.yaml"))),
                        Arguments.of(
                                driver,
                                "two profiles",
                                List.of(List.of(
                                        "user",
                                        "ops(alpha_workload.insert=1,beta_workload.insert=1)",
                                        "profile=" + CassandraStress.profile("cqlstress-example-specA.yaml") + ","
                                                + CassandraStress.profile("cqlstress-counter-example-specB.yaml"),
                                        "no-warmup",
                                        "n=10000",
                                        "cl=QUORUM",
                                        "-errors",
                                        "fail-fast",
                                        "-rate",
                                        "threads=4")))));
    }

    private static List<String> predefined(String command) {
        List<String> args = new ArrayList<>(List.of(command));
        args.addAll(PREDEFINED);
        return args;
    }

    private static List<String> profile(String name) {
        return List.of(
                "user",
                "ops(insert=1)",
                "profile=" + CassandraStress.profile(name),
                "no-warmup",
                "n=10000",
                "cl=QUORUM",
                "-errors",
                "fail-fast",
                "-rate",
                "threads=4");
    }

    @ParameterizedTest(name = "{1} with the {0} driver")
    @MethodSource("workloads")
    void runsTheWorkload(String driver, String name, List<List<String>> runs) throws Exception {
        for (List<String> run : runs) {
            StressResult result = launch(connected(run, driver));
            assertTrue(result.succeeded(), result::toString);
            assertEquals(0L, result.totalErrors().orElseThrow(), result::toString);
        }
    }

    @Test
    void writesTheGraph() throws Exception {
        Path html = dir.resolve("graph.html");
        StressResult result = launch(connected(
                List.of(
                        "write",
                        "n=1000",
                        "no-warmup",
                        "-rate",
                        "threads=2",
                        "-schema",
                        "keyspace=distribution_graph",
                        REPLICATION,
                        "-graph",
                        "file=" + html,
                        "title=distribution"),
                "native"));
        assertTrue(result.succeeded(), result::toString);
        assertTrue(Files.readString(html).contains("\"title\":\"distribution\""), result::toString);
    }

    private static List<String> connected(List<String> run, String driver) {
        List<String> args = new ArrayList<>(run);
        args.addAll(List.of(
                "-mode",
                "cql3",
                driver,
                "-node",
                ScyllaNode.host(),
                "datacenter=" + ScyllaNode.DATACENTER,
                "-port",
                "native=" + ScyllaNode.port()));
        return args;
    }

    @Test
    void printsTheVersionOfStressAndOfBothDrivers() throws Exception {
        StressResult result = launch(List.of("version"));
        assertEquals(0, result.exitCode(), result::toString);
        assertTrue(result.output().contains("Version: " + System.getProperty("stress.version")), result::toString);
        assertTrue(
                result.output().lines().anyMatch(l -> l.matches("scylla-java-driver: \\d+\\.\\d+\\.\\d+\\.\\d+")),
                result::toString);
        assertTrue(
                result.output().lines().anyMatch(l -> l.matches("scylla-java-driver-4x: \\d+\\.\\d+\\.\\d+\\.\\d+")),
                result::toString);
    }

    /** The launcher runs the JDK of this test, so the release of this JVM selects the collector. */
    @Test
    void runsOnTheCollectorOfTheJavaReleaseWithoutAJvmWarning() throws Exception {
        StressResult result = launch(List.of("version"), "-Xlog:gc,gc+init:stdout");
        assertEquals(0, result.exitCode(), result::toString);
        if (hasTheGenerationalZgcFix(Runtime.version()) && !isSmallLinuxMachine()) {
            assertTrue(result.output().contains("Using The Z Garbage Collector"), result::toString);
            assertTrue(result.output().contains("GC Workers for Young Generation"), result::toString);
        } else {
            assertTrue(result.output().contains("Using G1"), result::toString);
        }
        assertFalse(result.output().contains("VM warning"), result::toString);
    }

    /** The launcher selects G1 on Linux with less than 4 CPUs or less than 7.5 GiB of memory. */
    private static boolean isSmallLinuxMachine() {
        long memory = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getTotalMemorySize();
        return OS.LINUX.isCurrentOs() && (Runtime.getRuntime().availableProcessors() < 4 || memory < 7680L << 20);
    }

    /** The releases with the fix of JDK-8347564, the ranges of the UseZGC lines in conf/jvm-gc.options. */
    private static boolean hasTheGenerationalZgcFix(Runtime.Version version) {
        return version.compareToIgnoreOptional(Runtime.Version.parse("24.0.2")) >= 0
                || (version.feature() == 21 && version.compareToIgnoreOptional(Runtime.Version.parse("21.0.9")) >= 0);
    }

    @Test
    void runsOnTheCollectorThatJvmOptsSelects() throws Exception {
        StressResult result = launch(List.of("version"), "-XX:+UseG1GC -Xlog:gc:stdout");
        assertEquals(0, result.exitCode(), result::toString);
        assertTrue(result.output().contains("Using G1"), result::toString);
    }

    static Stream<Arguments> javaReleases() {
        String zgc = "-XX:+UseZGC";
        String g1 = "-XX:+UseG1GC";
        String generational = "-XX:+ZGenerational";
        String compact = "-XX:+UseCompactObjectHeaders";
        return Stream.of(
                Arguments.of("21.0.8", "", List.of(g1), List.of(zgc, generational, compact)),
                Arguments.of("21.0.9", "", List.of(zgc, generational), List.of(g1, compact)),
                Arguments.of("21.0.12", "", List.of(zgc, generational), List.of(g1, compact)),
                Arguments.of("22.0.2", "", List.of(g1), List.of(zgc, generational, compact)),
                Arguments.of("24.0.1", "", List.of(g1), List.of(zgc, generational, compact)),
                Arguments.of("24.0.2", "", List.of(zgc), List.of(g1, generational, compact)),
                Arguments.of("25", "", List.of(zgc, compact), List.of(g1, generational)),
                Arguments.of("25.0.1", "", List.of(zgc, compact), List.of(g1, generational)),
                Arguments.of("26-ea", "", List.of(zgc, compact), List.of(g1, generational)),
                Arguments.of("21.0.5", zgc, List.of(zgc), List.of(g1, generational)),
                Arguments.of("25.0.1", g1, List.of(g1), List.of(zgc)));
    }

    /** Runs the launcher with a fake JDK. */
    @ParameterizedTest(name = "Java {0} with JVM_OPTS {1}")
    @MethodSource("javaReleases")
    void selectsTheJvmOptionsOfTheJavaRelease(String version, String jvmOpts, List<String> present, List<String> absent)
            throws Exception {
        Path javaHome = fakeJavaHome(version, "jdk-" + version + "-" + jvmOpts.hashCode());
        StressResult result = launch(List.of("version"), Map.of("JAVA_HOME", javaHome.toString(), "JVM_OPTS", jvmOpts));
        assertEquals(0, result.exitCode(), result::toString);
        List<String> args = result.output().lines().toList();
        present.forEach(
                option -> assertEquals(1, args.stream().filter(option::equals).count(), result::toString));
        absent.forEach(option -> assertFalse(args.contains(option), result::toString));
        assertTrue(args.contains("-XX:MaxRAMPercentage=85"), result::toString);
    }

    @Test
    void readsEachJvmOptionsFileOfTheConfigurationDirectoryOnItsOwn() throws Exception {
        Path config = Files.createDirectories(dir.resolve("config"));
        Files.copy(HOME.resolve("conf/logback.xml"), config.resolve("logback.xml"));
        Files.writeString(config.resolve("jvm-gc.options"), "-XX:+UseSerialGC\n");
        Path javaHome = fakeJavaHome("25.0.1", "jdk-config");
        StressResult result = launch(
                List.of("version"),
                Map.of("JAVA_HOME", javaHome.toString(), "CASSANDRA_STRESS_CONFIG", config.toString()));
        assertEquals(0, result.exitCode(), result::toString);
        List<String> args = result.output().lines().toList();
        assertTrue(args.contains("-XX:+UseSerialGC"), result::toString);
        assertFalse(args.contains("-XX:+UseZGC"), result::toString);
        assertTrue(args.contains("-XX:MaxRAMPercentage=85"), result::toString);
    }

    /** On ZGC, the heap starts at 64 MiB, and the heap balancer of the process shares the memory. */
    @Test
    void leavesTheHeapToTheHeapBalancerOnZgc() throws Exception {
        Path javaHome = fakeJavaHome("25.0.1", "jdk-zgc-heap");
        StressResult result = launch(List.of("version"), Map.of("JAVA_HOME", javaHome.toString()));
        assertEquals(0, result.exitCode(), result::toString);
        List<String> args = result.output().lines().toList();
        assertEquals(
                List.of("-Xms64m"),
                args.stream().filter(a -> a.startsWith("-Xm")).toList(),
                result::toString);
        assertTrue(args.contains("-XX:ZUncommitDelay=1"), result::toString);
    }

    /** On Linux and G1, the launcher sets a fixed heap from the free memory for a workload. */
    @Test
    @EnabledOnOs(OS.LINUX)
    void setsAFixedHeapOnG1() throws Exception {
        Path javaHome = fakeJavaHome("21.0.5", "jdk-g1-heap");
        StressResult result = launch(List.of("write", "n=1"), Map.of("JAVA_HOME", javaHome.toString()));
        assertEquals(0, result.exitCode(), result::toString);
        List<String> args = result.output().lines().toList();
        String xms = args.stream().filter(a -> a.startsWith("-Xms")).findFirst().orElseThrow();
        assertTrue(xms.matches("-Xms\\d+m"), result::toString);
        assertTrue(args.contains("-Xmx" + xms.substring(4)), result::toString);
    }

    @Test
    void keepsTheHeapOfJvmOpts() throws Exception {
        Path javaHome = fakeJavaHome("21.0.5", "jdk-xmx");
        StressResult result =
                launch(List.of("write", "n=1"), Map.of("JAVA_HOME", javaHome.toString(), "JVM_OPTS", "-Xmx1g"));
        assertEquals(0, result.exitCode(), result::toString);
        List<String> args = result.output().lines().toList();
        assertEquals(1, args.stream().filter(a -> a.startsWith("-Xm")).count(), result::toString);
        assertTrue(args.contains("-Xmx1g"), result::toString);
        assertTrue(args.contains("-Dcassandra.stress.heap.balance=false"), result::toString);
    }

    @Test
    void keepsTheHeapOfTheOptionsFile() throws Exception {
        Path config = Files.createDirectories(dir.resolve("heap-config"));
        Files.copy(HOME.resolve("conf/logback.xml"), config.resolve("logback.xml"));
        Files.writeString(
                config.resolve("jvm-clients.options"),
                Files.readString(HOME.resolve("conf/jvm-clients.options")) + "-Xmx300m\n");
        Path javaHome = fakeJavaHome("21.0.5", "jdk-file-xmx");
        StressResult result = launch(
                List.of("write", "n=1"),
                Map.of("JAVA_HOME", javaHome.toString(), "CASSANDRA_STRESS_CONFIG", config.toString()));
        assertEquals(0, result.exitCode(), result::toString);
        List<String> args = result.output().lines().toList();
        assertEquals(
                List.of("-Xmx300m"),
                args.stream().filter(a -> a.startsWith("-Xm")).toList(),
                result::toString);
        assertTrue(args.contains("-Dcassandra.stress.heap.balance=false"), result::toString);
    }

    /** The version command keeps the default heap of the JVM on G1. */
    @Test
    void keepsTheDefaultHeapForTheVersionOnG1() throws Exception {
        Path javaHome = fakeJavaHome("21.0.5", "jdk-g1-version");
        StressResult result = launch(List.of("version"), Map.of("JAVA_HOME", javaHome.toString()));
        assertEquals(0, result.exitCode(), result::toString);
        assertTrue(result.output().lines().noneMatch(a -> a.startsWith("-Xm")), result::toString);
    }

    /** Writes a JDK whose release file names {@code version} and whose java prints its arguments one per line. */
    private Path fakeJavaHome(String version, String name) throws IOException {
        Path javaHome = dir.resolve(name);
        Path java = Files.createDirectories(javaHome.resolve("bin")).resolve("java");
        Files.writeString(javaHome.resolve("release"), "JAVA_VERSION=\"" + version + "\"\n");
        Files.writeString(java, "#!/bin/sh\nprintf '%s\\n' \"$@\"\n");
        assertTrue(java.toFile().setExecutable(true));
        return javaHome;
    }

    private StressResult launch(List<String> args) throws IOException, InterruptedException {
        return launch(args, Map.of());
    }

    private StressResult launch(List<String> args, String jvmOpts) throws IOException, InterruptedException {
        return launch(args, Map.of("JVM_OPTS", jvmOpts));
    }

    /** Runs the launcher with {@code env}. A JVM_OPTS in it goes after the JVM_OPTS of the test task. */
    private StressResult launch(List<String> args, Map<String, String> env) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(HOME.resolve("bin/cassandra-stress").toString());
        command.addAll(args);
        Path output = Files.createTempFile(dir, "launcher-", ".log");
        ProcessBuilder builder =
                new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        Map<String, String> environment = builder.environment();
        environment.put("CASSANDRA_STRESS_STORAGE_DIR", dir.toString());
        env.forEach((name, value) -> {
            if ("JVM_OPTS".equals(name)) {
                environment.merge(name, value, (current, extra) -> current + " " + extra);
            } else {
                environment.put(name, value);
            }
        });
        Process process = builder.start();
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            return new StressResult(-1, "timed out\n" + Files.readString(output));
        }
        return new StressResult(process.exitValue(), Files.readString(output));
    }
}
