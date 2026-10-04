package org.apache.cassandra.stress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class CassandraStress
{
    private static final AtomicInteger RUNS = new AtomicInteger();

    private final Path workDir;
    private final Driver driver;

    enum Driver
    {
        V3("native"),
        V4("4x");

        final String mode;

        Driver(String mode)
        {
            this.mode = mode;
        }
    }

    CassandraStress(Path workDir, Driver driver)
    {
        this.workDir = workDir;
        this.driver = driver;
    }

    StressResult run(String... args)
    {
        Path log = workDir.resolve("stress-" + RUNS.incrementAndGet() + ".log");
        List<String> full = new ArrayList<>(List.of(args));
        int logIndex = full.indexOf("-log");
        if (logIndex >= 0)
            full.add(logIndex + 1, "file=" + log);
        else
            full.addAll(List.of("-log", "file=" + log, "interval=1s"));
        if (!full.contains("-mode"))
            full.addAll(List.of("-mode", "cql3", driver.mode));
        full.addAll(List.of("-node", ScyllaNode.host(), "datacenter=" + ScyllaNode.DATACENTER,
                            "-port", "native=" + ScyllaNode.port()));

        int exitCode = Stress.run(full.toArray(String[]::new));
        try
        {
            return new StressResult(exitCode, Files.exists(log) ? Files.readString(log) : "");
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    static String profile(String name)
    {
        return Path.of("examples", name).toAbsolutePath().toString();
    }
}
