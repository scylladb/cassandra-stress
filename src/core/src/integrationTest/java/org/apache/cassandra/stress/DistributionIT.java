package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

    private StressResult launch(List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(HOME.resolve("bin/cassandra-stress").toString());
        command.addAll(args);
        Path output = Files.createTempFile(dir, "launcher-", ".log");
        ProcessBuilder builder =
                new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("CASSANDRA_STRESS_STORAGE_DIR", dir.toString());
        Process process = builder.start();
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            return new StressResult(-1, "timed out\n" + Files.readString(output));
        }
        return new StressResult(process.exitValue(), Files.readString(output));
    }
}
