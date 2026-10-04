package org.apache.cassandra.stress;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.HdrHistogram.HistogramLogReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.cassandra.stress.CassandraStress.Driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputsIT
{
    private static final String REPLICATION = "replication(strategy=NetworkTopologyStrategy,replication_factor=1)";

    @TempDir
    Path dir;

    @Test
    void writesTheGraphAndTheHdrLog() throws Exception
    {
        ScyllaNode.dropKeyspace("outputs");
        CassandraStress stress = new CassandraStress(dir, Driver.V4);
        Path html = dir.resolve("graph.html");
        Path hdr = dir.resolve("stress.hdr");

        StressResult result = stress.run("write", "n=1000", "no-warmup", "-rate", "threads=2",
                                         "-schema", "keyspace=outputs", REPLICATION,
                                         "-graph", "file=" + html, "title=it",
                                         "-log", "hdrfile=" + hdr);
        assertTrue(result.succeeded(), result::toString);
        assertTrue(Files.readString(html).contains("\"title\":\"it\""));

        try (HistogramLogReader reader = new HistogramLogReader(hdr.toFile()))
        {
            assertNotNull(reader.nextIntervalHistogram());
        }
    }

    @Test
    void stressdRunsACommandSentWithSendTo() throws Exception
    {
        ScyllaNode.dropKeyspace("stressd");
        Thread server = Thread.ofPlatform().daemon().name("stressd").start(() -> {
            try
            {
                StressServer.main(new String[]{ "-h", "127.0.0.1" });
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        });
        TimeUnit.SECONDS.sleep(1);
        assertTrue(server.isAlive());

        StressResult result = new CassandraStress(dir, Driver.V3).run("write", "n=500", "no-warmup", "-rate", "threads=2",
                                                                     "-schema", "keyspace=stressd", REPLICATION,
                                                                     "-send-to", "127.0.0.1");
        assertEquals(0, result.exitCode(), result::toString);
        assertEquals(0L, result.totalErrors().orElseThrow(), result::toString);
        assertTrue(result.output().contains("Op rate"), result::toString);
    }
}
