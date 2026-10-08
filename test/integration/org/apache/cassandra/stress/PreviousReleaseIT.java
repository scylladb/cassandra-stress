package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.IndefiniteWaitOneShotStartupCheckStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

class PreviousReleaseIT {
    private static final String RELEASE =
            System.getProperty("stress.previous.image", "scylladb/cassandra-stress:3.21.1");
    private static final String REPLICATION = "replication(strategy=NetworkTopologyStrategy,replication_factor=1)";

    private static final String MIXED = """
        keyspace: released
        keyspace_definition: |
          CREATE KEYSPACE released WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 1};
        table: mixed
        table_definition: |
          CREATE TABLE mixed (pk bigint, c_blob blob, c_timeuuid timeuuid, v_text text, v_time time,
            v_timestamp timestamp, v_uuid uuid, v_inet inet, v_date date, v_double double,
            PRIMARY KEY (pk, c_blob, c_timeuuid)) WITH CLUSTERING ORDER BY (c_blob DESC, c_timeuuid ASC)
        columnspec:
          - name: c_blob
            size: fixed(4)
            cluster: fixed(5)
          - name: c_timeuuid
            cluster: fixed(4)
          - name: v_time
            population: uniform(1..80000000000000)
          - name: v_date
            population: uniform(1..20000)
        insert:
          partitions: fixed(1)
          select: fixed(1)/1
          batchtype: UNLOGGED
        queries:
          bykey:
            cql: select * from mixed where pk = ?
        """;

    private static final String DESCENDING = """
        keyspace: released
        keyspace_definition: |
          CREATE KEYSPACE released WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 1};
        table: descending
        table_definition: |
          CREATE TABLE descending (pk bigint, c_inet inet, c_date date, v_text text, v_blob blob,
            PRIMARY KEY (pk, c_inet, c_date)) WITH CLUSTERING ORDER BY (c_inet DESC, c_date DESC)
        columnspec:
          - name: c_inet
            cluster: fixed(5)
          - name: c_date
            cluster: fixed(4)
            population: uniform(1..20000)
        insert:
          partitions: fixed(1)
          select: fixed(1)/1
          batchtype: UNLOGGED
        queries:
          bykey:
            cql: select * from descending where pk = ?
        """;

    @TempDir
    Path dir;

    private static String runReleased(String profile, String... args) {
        List<String> command = new ArrayList<>(List.of(args));
        command.addAll(List.of("-node", ScyllaNode.NETWORK_ALIAS));
        ScyllaNode.container();
        try (GenericContainer<?> released = new GenericContainer<>(DockerImageName.parse(RELEASE))
                .withNetwork(ScyllaNode.NETWORK)
                .withCopyToContainer(Transferable.of(profile), "/profile.yaml")
                .withCommand(command.toArray(String[]::new))
                .withStartupCheckStrategy(new IndefiniteWaitOneShotStartupCheckStrategy())) {
            released.start();
            return released.getLogs();
        }
    }

    @Test
    void readsAndValidatesTheRowsThatThePreviousReleaseWrote() {
        ScyllaNode.dropKeyspace("released_predefined");
        String written = runReleased(
                MIXED,
                "write",
                "n=5000",
                "no-warmup",
                "-col",
                "size=FIXED(1024) n=FIXED(1)",
                "-pop",
                "seq=1..5000",
                "-rate",
                "threads=4",
                "-schema",
                "keyspace=released_predefined",
                REPLICATION);
        assertTrue(written.contains("\nEND"), written);

        CassandraStress stress = new CassandraStress(dir);
        StressResult read = stress.run(
                "read",
                "n=5000",
                "no-warmup",
                "-col",
                "size=FIXED(1024) n=FIXED(1)",
                "-pop",
                "seq=1..5000",
                "-rate",
                "threads=4",
                "-schema",
                "keyspace=released_predefined");
        assertTrue(read.succeeded(), read::toString);
        assertEquals(0L, read.totalErrors().orElseThrow(), read::toString);

        StressResult wrongSize = stress.run(
                "read",
                "n=200",
                "no-warmup",
                "-col",
                "size=FIXED(512) n=FIXED(1)",
                "-pop",
                "seq=1..200",
                "-rate",
                "threads=1",
                "-errors",
                "ignore",
                "-schema",
                "keyspace=released_predefined");
        assertEquals(200L, wrongSize.totalErrors().orElseThrow(), wrongSize::toString);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mixed", "descending"})
    void validatesTheClusteredRowsThatThePreviousReleaseWrote(String table) throws Exception {
        String profileYaml = "mixed".equals(table) ? MIXED : DESCENDING;
        ScyllaNode.dropKeyspace("released");
        String written = runReleased(
                profileYaml,
                "user",
                "profile=/profile.yaml",
                "ops(insert=1)",
                "no-warmup",
                "n=300",
                "-pop",
                "seq=1..300",
                "-rate",
                "threads=4");
        assertTrue(written.contains("\nEND"), written);
        assertEquals(300L * 20, ScyllaNode.count("released", table));

        Path profile = Files.writeString(dir.resolve(table + ".yaml"), profileYaml);
        StressResult validate = new CassandraStress(dir)
                .run(
                        "user",
                        "profile=" + profile,
                        "ops(validate=1)",
                        "no-warmup",
                        "n=600",
                        "-pop",
                        "seq=1..300",
                        "-rate",
                        "threads=4",
                        "-errors",
                        "fail-fast");
        assertTrue(validate.succeeded(), validate::toString);
        assertEquals(0L, validate.totalErrors().orElseThrow(), validate::toString);
    }
}
