package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PredefinedCommandsIT {
    private static final String REPLICATION = "replication(strategy=NetworkTopologyStrategy,replication_factor=1)";

    @TempDir
    Path dir;

    private static String keyspace(String keyspace) {
        ScyllaNode.dropKeyspace(keyspace);
        return keyspace;
    }

    @Test
    void readValidatesEveryRowThatWriteInserted() {
        CassandraStress stress = new CassandraStress(dir);
        String keyspace = keyspace("write_read");

        StressResult write = stress.run(
                "write",
                "n=2000",
                "cl=ONE",
                "no-warmup",
                "-pop",
                "seq=1..2000",
                "-col",
                "n=fixed(5)",
                "size=fixed(32)",
                "-rate",
                "threads=4",
                "-schema",
                "keyspace=" + keyspace,
                REPLICATION);
        assertTrue(write.succeeded(), write::toString);
        assertEquals(2000, ScyllaNode.count(keyspace, "standard1"));

        StressResult read = stress.run(
                "read",
                "n=2000",
                "cl=ONE",
                "no-warmup",
                "-pop",
                "seq=1..2000",
                "-col",
                "n=fixed(5)",
                "size=fixed(32)",
                "-rate",
                "threads=4",
                "-errors",
                "fail-fast",
                "-schema",
                "keyspace=" + keyspace);
        assertTrue(read.succeeded(), read::toString);
        assertEquals(0L, read.totalErrors().orElseThrow());
    }

    @Test
    void readFailsWhenTheDataDoesNotMatch() {
        CassandraStress stress = new CassandraStress(dir);
        String keyspace = keyspace("mismatch");

        assertTrue(stress.run(
                        "write",
                        "n=200",
                        "no-warmup",
                        "-pop",
                        "seq=1..200",
                        "-col",
                        "size=fixed(32)",
                        "-rate",
                        "threads=2",
                        "-schema",
                        "keyspace=" + keyspace,
                        REPLICATION)
                .succeeded());

        StressResult read = stress.run(
                "read",
                "n=200",
                "no-warmup",
                "-pop",
                "seq=1..200",
                "-col",
                "size=fixed(16)",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast",
                "-schema",
                "keyspace=" + keyspace);
        assertFalse(read.succeeded(), read::toString);
    }

    @Test
    void mixedRunsBothOperations() {
        CassandraStress stress = new CassandraStress(dir);
        String keyspace = keyspace("mixed");

        assertTrue(stress.run(
                        "write",
                        "n=500",
                        "no-warmup",
                        "-pop",
                        "seq=1..500",
                        "-rate",
                        "threads=2",
                        "-schema",
                        "keyspace=" + keyspace,
                        REPLICATION)
                .succeeded());
        StressResult mixed = stress.run(
                "mixed",
                "ratio(write=1,read=1)",
                "n=1000",
                "no-warmup",
                "-pop",
                "seq=1..500",
                "-rate",
                "threads=2",
                "-schema",
                "keyspace=" + keyspace);
        assertTrue(mixed.succeeded(), mixed::toString);
        assertTrue(mixed.output().contains("WRITE") && mixed.output().contains("READ"), mixed::toString);
        assertEquals(1000L, mixed.totalPartitions().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lz4", "snappy"})
    void unpreparedStatementsWithCompressionWork(String compression) {
        CassandraStress stress = new CassandraStress(dir);
        String keyspace = keyspace("unprepared_" + compression);

        StressResult write = stress.run(
                "write",
                "n=300",
                "no-warmup",
                "-pop",
                "seq=1..300",
                "-rate",
                "threads=2",
                "-mode",
                "cql3",
                CassandraStress.driver(),
                "unprepared",
                "compression=" + compression,
                "-schema",
                "keyspace=" + keyspace,
                REPLICATION);
        assertTrue(write.succeeded(), write::toString);
        assertEquals(300, ScyllaNode.count(keyspace, "standard1"));
    }

    @Test
    void fixedRateRunsForTheGivenDuration() {
        CassandraStress stress = new CassandraStress(dir);
        String keyspace = keyspace("duration");

        StressResult write = stress.run(
                "write",
                "duration=3s",
                "no-warmup",
                "-rate",
                "threads=2",
                "fixed=200/s",
                "-schema",
                "keyspace=" + keyspace,
                REPLICATION);
        assertTrue(write.succeeded(), write::toString);
        long ops = write.totalPartitions().orElseThrow();
        assertTrue(ops > 200 && ops < 1200, write::toString);
    }

    @Test
    void runsOnTheDriverThatTheModeSelects() {
        CassandraStress stress = new CassandraStress(dir);
        String keyspace = keyspace("driver_" + CassandraStress.driver());
        String api = "4x".equals(CassandraStress.driver()) ? "JAVA_DRIVER4_NATIVE" : "JAVA_DRIVER_NATIVE";

        StressResult write = stress.run(
                "write", "n=10", "no-warmup", "-rate", "threads=1", "-schema", "keyspace=" + keyspace, REPLICATION);

        assertTrue(write.succeeded(), write::toString);
        assertTrue(write.output().contains("  API: " + api), write::toString);
        assertEquals(10, ScyllaNode.count(keyspace, "standard1"));
    }

    @Test
    void truncatesAMixedCaseKeyspace() {
        ScyllaNode.dropKeyspace("\"MixedCaseKs\"");
        CassandraStress stress = new CassandraStress(dir);
        StressResult first = stress.run(
                "write", "n=100", "no-warmup", "-rate", "threads=2", "-schema", "keyspace=MixedCaseKs", REPLICATION);
        assertTrue(first.succeeded(), first::toString);

        StressResult truncated = stress.run(
                "write",
                "n=10",
                "no-warmup",
                "truncate=once",
                "-pop",
                "seq=1..10",
                "-rate",
                "threads=1",
                "-schema",
                "keyspace=MixedCaseKs",
                REPLICATION);
        assertTrue(truncated.succeeded(), truncated::toString);
        assertEquals(10, ScyllaNode.count("\"MixedCaseKs\"", "standard1"));
    }
}
