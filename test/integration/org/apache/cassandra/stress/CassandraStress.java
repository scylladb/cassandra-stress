package org.apache.cassandra.stress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class CassandraStress {
    private static final AtomicInteger RUNS = new AtomicInteger();

    private final Path workDir;

    CassandraStress(Path workDir) {
        this.workDir = workDir;
    }

    StressResult run(String... args) {
        Path log = workDir.resolve("stress-" + RUNS.incrementAndGet() + ".log");
        List<String> full = new ArrayList<>(List.of(args));
        int logIndex = full.indexOf("-log");
        if (logIndex >= 0) full.add(logIndex + 1, "file=" + log);
        else full.addAll(List.of("-log", "file=" + log, "interval=1s"));
        if (!full.contains("-node"))
            full.addAll(List.of("-node", ScyllaNode.host(), "datacenter=" + ScyllaNode.DATACENTER));
        full.addAll(List.of("-port", "native=" + ScyllaNode.port()));

        int exitCode = Stress.run(full.toArray(String[]::new));
        try {
            return new StressResult(exitCode, Files.exists(log) ? Files.readString(log) : "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String profile(String name) {
        return Path.of("examples", name).toAbsolutePath().toString();
    }
}
