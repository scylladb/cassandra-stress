package org.apache.cassandra.stress;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.apache.cassandra.stress.CassandraStress.Driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredefinedCommandsIT
{
    private static final String REPLICATION = "replication(strategy=NetworkTopologyStrategy,replication_factor=1)";

    @TempDir
    Path dir;

    private static String keyspace(String test, Driver driver)
    {
        String keyspace = (test + "_" + driver).toLowerCase(java.util.Locale.ROOT);
        ScyllaNode.dropKeyspace(keyspace);
        return keyspace;
    }

    @ParameterizedTest
    @EnumSource(Driver.class)
    void readValidatesEveryRowThatWriteInserted(Driver driver)
    {
        CassandraStress stress = new CassandraStress(dir, driver);
        String keyspace = keyspace("write_read", driver);

        StressResult write = stress.run("write", "n=2000", "cl=ONE", "no-warmup", "-pop", "seq=1..2000",
                                        "-col", "n=fixed(5)", "size=fixed(32)", "-rate", "threads=4",
                                        "-schema", "keyspace=" + keyspace, REPLICATION);
        assertTrue(write.succeeded(), write::toString);
        assertEquals(2000, ScyllaNode.count(keyspace, "standard1"));

        StressResult read = stress.run("read", "n=2000", "cl=ONE", "no-warmup", "-pop", "seq=1..2000",
                                       "-col", "n=fixed(5)", "size=fixed(32)", "-rate", "threads=4",
                                       "-errors", "fail-fast", "-schema", "keyspace=" + keyspace);
        assertTrue(read.succeeded(), read::toString);
        assertEquals(0L, read.totalErrors().orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Driver.class)
    void readFailsWhenTheDataDoesNotMatch(Driver driver)
    {
        CassandraStress stress = new CassandraStress(dir, driver);
        String keyspace = keyspace("mismatch", driver);

        assertTrue(stress.run("write", "n=200", "no-warmup", "-pop", "seq=1..200", "-col", "size=fixed(32)",
                              "-rate", "threads=2", "-schema", "keyspace=" + keyspace, REPLICATION).succeeded());

        StressResult read = stress.run("read", "n=200", "no-warmup", "-pop", "seq=1..200", "-col", "size=fixed(16)",
                                       "-rate", "threads=2", "-errors", "fail-fast", "-schema", "keyspace=" + keyspace);
        assertFalse(read.succeeded(), read::toString);
    }

    @ParameterizedTest
    @EnumSource(Driver.class)
    void mixedRunsBothOperations(Driver driver)
    {
        CassandraStress stress = new CassandraStress(dir, driver);
        String keyspace = keyspace("mixed", driver);

        assertTrue(stress.run("write", "n=500", "no-warmup", "-pop", "seq=1..500", "-rate", "threads=2",
                              "-schema", "keyspace=" + keyspace, REPLICATION).succeeded());
        StressResult mixed = stress.run("mixed", "ratio(write=1,read=1)", "n=1000", "no-warmup", "-pop", "seq=1..500",
                                        "-rate", "threads=2", "-schema", "keyspace=" + keyspace);
        assertTrue(mixed.succeeded(), mixed::toString);
        assertTrue(mixed.output().contains("WRITE") && mixed.output().contains("READ"), mixed::toString);
        assertEquals(1000L, mixed.totalPartitions().orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Driver.class)
    void unpreparedStatementsWithCompressionWork(Driver driver)
    {
        CassandraStress stress = new CassandraStress(dir, driver);
        String keyspace = keyspace("unprepared", driver);

        StressResult write = stress.run("write", "n=300", "no-warmup", "-pop", "seq=1..300", "-rate", "threads=2",
                                        "-mode", "cql3", driver.mode, "unprepared", "compression=lz4",
                                        "-schema", "keyspace=" + keyspace, REPLICATION);
        assertTrue(write.succeeded(), write::toString);
        assertEquals(300, ScyllaNode.count(keyspace, "standard1"));
    }

    @ParameterizedTest
    @EnumSource(Driver.class)
    void fixedRateRunsForTheGivenDuration(Driver driver)
    {
        CassandraStress stress = new CassandraStress(dir, driver);
        String keyspace = keyspace("duration", driver);

        StressResult write = stress.run("write", "duration=3s", "no-warmup", "-rate", "threads=2", "fixed=200/s",
                                        "-schema", "keyspace=" + keyspace, REPLICATION);
        assertTrue(write.succeeded(), write::toString);
        long ops = write.totalPartitions().orElseThrow();
        assertTrue(ops > 200 && ops < 1200, write::toString);
    }
}
