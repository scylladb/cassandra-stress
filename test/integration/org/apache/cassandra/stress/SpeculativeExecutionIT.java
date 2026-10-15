package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpeculativeExecutionIT {
    private static final int PAUSED = 1;
    private static final int REQUEST_TIMEOUT_MS = 2000;

    private static ScyllaCluster cluster;

    @TempDir
    Path dir;

    @BeforeAll
    static void startCluster() {
        cluster = new ScyllaCluster(3);
    }

    @AfterAll
    static void stopCluster() {
        if (cluster != null) {
            cluster.close();
        }
    }

    @AfterEach
    void resumeThePausedNode() throws IOException, InterruptedException {
        if (cluster.isPaused(PAUSED)) {
            cluster.unpause(PAUSED);
        }
        cluster.awaitAllUp(Duration.ofMinutes(2));
    }

    @Test
    void withoutSpeculationACoordinatorThatStopsAnsweringFailsTheRun() throws Exception {
        StressResult run = writeAndPauseANode(dir, "speculative_off", List.of());

        assertFalse(run.succeeded(), run::toString);
        assertTrue(run.output().toLowerCase(Locale.ROOT).contains("timed out"), run::toString);
    }

    @Test
    void withSpeculationACoordinatorThatStopsAnsweringCostsOnlyTheDelay() throws Exception {
        StressResult run = writeAndPauseANode(
                dir, "speculative_on", List.of("idempotent", "speculativeExecutions=3", "speculativeDelay=100"));

        assertTrue(run.succeeded(), run::toString);
        assertEquals(0L, run.totalErrors().orElseThrow(), run::toString);
        double maxLatency = run.maxLatencyMillis().orElseThrow();
        assertTrue(maxLatency >= 100 && maxLatency < REQUEST_TIMEOUT_MS / 2.0, run::toString);
    }

    private static StressResult writeAndPauseANode(Path dir, String keyspace, List<String> modeOptions)
            throws Exception {
        cluster.dropKeyspace(keyspace);
        StressResult schema = write(Files.createDirectories(dir.resolve("schema")), keyspace, "n=1000", List.of());
        assertTrue(schema.succeeded(), schema::toString);
        CompletableFuture<StressResult> run =
                CompletableFuture.supplyAsync(() -> write(dir, keyspace, "duration=8s", modeOptions));
        awaitProgress(dir, run);
        awaitRequestsOn(PAUSED, run);
        cluster.pause(PAUSED);
        return run.get(2, TimeUnit.MINUTES);
    }

    private static void awaitRequestsOn(int index, CompletableFuture<StressResult> run)
            throws IOException, InterruptedException {
        long before = cluster.requestsServed(index);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline) && !run.isDone()) {
            if (cluster.requestsServed(index) > before + 100) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Node " + index + " coordinated no request of the run");
    }

    private static void awaitProgress(Path dir, CompletableFuture<StressResult> run)
            throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(1));
        while (Instant.now().isBefore(deadline) && !run.isDone()) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path log :
                        files.filter(path -> path.toString().endsWith(".log")).toList()) {
                    if (Files.readString(log).lines().anyMatch(line -> line.startsWith("total,"))) {
                        return;
                    }
                }
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("The stress run showed no progress");
    }

    private static StressResult write(Path dir, String keyspace, String count, List<String> modeOptions) {
        List<String> args = new ArrayList<>(List.of(
                "write",
                count,
                "cl=QUORUM",
                "no-warmup",
                "-rate",
                "threads=8",
                "-errors",
                "retries=0",
                "fail-fast",
                "-schema",
                "keyspace=" + keyspace,
                "replication(factor=3)",
                "-node",
                cluster.host(),
                "datacenter=" + ScyllaNode.DATACENTER,
                "-port",
                "native=" + cluster.port(),
                "-mode",
                "cql3",
                CassandraStress.driver(),
                "requestTimeout=" + REQUEST_TIMEOUT_MS));
        args.addAll(modeOptions);
        return new CassandraStress(dir).run(args.toArray(String[]::new));
    }
}
