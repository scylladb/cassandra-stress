package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DriverOptionsIT {
    private static final String REPLICATION = "replication(strategy=NetworkTopologyStrategy,replication_factor=1)";

    @TempDir
    Path dir;

    private Path profile(String table, boolean clustered, String columns, String columnspec, String queries)
            throws IOException {
        String yaml = """
            keyspace: driveroptions
            keyspace_definition: |
              CREATE KEYSPACE driveroptions WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 1};
            table: %s
            table_definition: |
              CREATE TABLE %s (%s, %s, PRIMARY KEY %s)
            columnspec:
              - name: pk
                population: uniform(1..100B)
            %s
            %s
            insert:
              partitions: fixed(1)
              batchtype: UNLOGGED
            queries:
            %s
            """.formatted(
                        table,
                        table,
                        clustered ? "pk int, ck int" : "pk int",
                        columns,
                        clustered ? "(pk, ck)" : "(pk)",
                        clustered ? "  - name: ck\n    cluster: fixed(2)" : "",
                        columnspec,
                        queries);
        Path file = dir.resolve(table + ".yaml");
        Files.writeString(file, yaml);
        return file;
    }

    @Test
    void bindsGeneratedDatesTimesAndCollections() throws IOException {
        ScyllaNode.dropKeyspace("driveroptions");
        Path profile = profile("typed", true, "tags set<text>, nums list<int>, day date, at time", """
              - name: tags
                size: uniform(1..3)
              - name: nums
                size: uniform(1..3)
              - name: day
                population: uniform(1..1000)
              - name: at
                population: uniform(1..1000000)\
            """, """
              bykey:
                cql: select * from typed where pk = ?
                fields: samerow\
            """);
        CassandraStress stress = new CassandraStress(dir);

        StressResult insert = stress.run(
                "user",
                "profile=" + profile,
                "ops(insert=1)",
                "no-warmup",
                "n=100",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast");
        assertTrue(insert.succeeded(), insert::toString);

        StressResult query = stress.run(
                "user",
                "profile=" + profile,
                "ops(bykey=1)",
                "no-warmup",
                "n=50",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast");
        assertTrue(query.succeeded(), query::toString);
        assertEquals(0L, query.totalErrors().orElseThrow());

        try (CqlSession session = ScyllaNode.session()) {
            List<Row> rows = session.execute("SELECT tags, nums, day, at FROM driveroptions.typed")
                    .all();
            assertFalse(rows.isEmpty());
            for (Row row : rows) {
                assertFalse(row.getSet("tags", String.class).isEmpty());
                assertFalse(row.getList("nums", Integer.class).isEmpty());
                LocalDate day = row.getLocalDate("day");
                assertTrue(day.getYear() >= 1970 && day.getYear() <= 1973, day::toString);
                LocalTime at = row.getLocalTime("at");
                assertTrue(at.toNanoOfDay() <= 1_000_000L, at::toString);
            }
        }
    }

    @Test
    void validatesClusteredRowsWithCollectionsDatesAndTimes() throws IOException {
        ScyllaNode.dropKeyspace("driveroptions");
        Path profile = profile(
                "validated",
                true,
                "tags set<text>, nums list<int>, day date, at time, label text, m map<text, int>",
                """
                  - name: tags
                    size: uniform(1..3)
                  - name: nums
                    size: uniform(1..3)
                  - name: day
                    population: uniform(1..1000)
                  - name: at
                    population: uniform(1..1000000)\
                """,
                """
                  bykey:
                    cql: select * from validated where pk = ?
                    fields: samerow\
                """);
        CassandraStress stress = new CassandraStress(dir);

        StressResult insert = stress.run(
                "user",
                "profile=" + profile,
                "ops(insert=1)",
                "no-warmup",
                "n=100",
                "-pop",
                "seq=1..100",
                "-rate",
                "threads=1",
                "-errors",
                "fail-fast",
                "skip-unsupported-columns");
        assertTrue(insert.succeeded(), insert::toString);

        StressResult validate = stress.run(
                "user",
                "profile=" + profile,
                "ops(validate=1)",
                "no-warmup",
                "n=100",
                "-pop",
                "seq=1..100",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast",
                "skip-unsupported-columns");
        assertTrue(validate.succeeded(), validate::toString);
        assertEquals(0L, validate.totalErrors().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"whitelist", "loadbalance=rr", "loadbalance=dc", "rack=rack1"})
    void connectsWithEachNodeOption(String option) {
        String keyspace = "node_" + option.replaceAll("[^a-z0-9]", "_");
        ScyllaNode.dropKeyspace(keyspace);
        CassandraStress stress = new CassandraStress(dir);

        StressResult write = stress.run(
                "write",
                "n=200",
                "no-warmup",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast",
                "-schema",
                "keyspace=" + keyspace,
                REPLICATION,
                "-node",
                ScyllaNode.host(),
                "datacenter=" + ScyllaNode.DATACENTER,
                option);
        assertTrue(write.succeeded(), write::toString);
        assertEquals(200, ScyllaNode.count(keyspace, "standard1"));
    }

    @Test
    void infersTheLocalDatacenterFromTheContactPoint() {
        ScyllaNode.dropKeyspace("inferred_dc");
        StressResult write = new CassandraStress(dir)
                .run(
                        "write",
                        "n=100",
                        "no-warmup",
                        "-rate",
                        "threads=1",
                        "-schema",
                        "keyspace=inferred_dc",
                        REPLICATION,
                        "-node",
                        ScyllaNode.host());
        assertTrue(write.succeeded(), write::toString);
        assertEquals(100, ScyllaNode.count("inferred_dc", "standard1"));
    }

    @Test
    void sweepsEveryPartitionOfTheRing() throws IOException {
        ScyllaNode.dropKeyspace("driveroptions");
        String yaml = """
            keyspace: driveroptions
            keyspace_definition: |
              CREATE KEYSPACE driveroptions WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 1};
            table: swept
            table_definition: |
              CREATE TABLE swept (pk bigint PRIMARY KEY, label text)
            columnspec:
              - name: pk
                population: uniform(1..100B)
            insert:
              partitions: fixed(1)
              batchtype: UNLOGGED
            queries:
              bykey:
                cql: select * from swept where pk = ?
            token_range_queries:
              everything:
                columns: '*'
                page_size: 10000
            """;
        Path profile = Files.writeString(dir.resolve("swept.yaml"), yaml);
        CassandraStress stress = new CassandraStress(dir);

        StressResult insert = stress.run(
                "user",
                "profile=" + profile,
                "ops(insert=1)",
                "no-warmup",
                "n=5000",
                "-pop",
                "seq=1..5000",
                "-rate",
                "threads=4",
                "-errors",
                "fail-fast");
        assertTrue(insert.succeeded(), insert::toString);
        long rows = ScyllaNode.count("driveroptions", "swept");
        assertEquals(5000, rows);

        StressResult sweep = stress.run(
                "user",
                "profile=" + profile,
                "ops(everything=1)",
                "no-warmup",
                "n=5000",
                "-rate",
                "threads=1",
                "-errors",
                "fail-fast");
        assertTrue(sweep.succeeded(), sweep::toString);
        assertEquals(rows, sweep.totalPartitions().orElseThrow(), sweep::toString);
    }

    @Test
    void validatesDescendingBlobAndTimeuuidClustering() throws IOException {
        ScyllaNode.dropKeyspace("driveroptions");
        Path profile = Files.writeString(dir.resolve("descending.yaml"), """
            keyspace: driveroptions
            keyspace_definition: |
              CREATE KEYSPACE driveroptions WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 1};
            table: descending
            table_definition: |
              CREATE TABLE descending (pk bigint, c_blob blob, c_timeuuid timeuuid, v text,
                PRIMARY KEY (pk, c_blob, c_timeuuid)) WITH CLUSTERING ORDER BY (c_blob DESC, c_timeuuid DESC)
            columnspec:
              - name: c_blob
                size: fixed(4)
                cluster: fixed(6)
              - name: c_timeuuid
                cluster: fixed(4)
            insert:
              partitions: fixed(1)
              select: fixed(1)/1
              batchtype: UNLOGGED
            queries:
              bykey:
                cql: select * from descending where pk = ?
            """);
        CassandraStress stress = new CassandraStress(dir);

        StressResult insert = stress.run(
                "user",
                "profile=" + profile,
                "ops(insert=1)",
                "no-warmup",
                "n=100",
                "-pop",
                "seq=1..100",
                "-rate",
                "threads=2");
        assertTrue(insert.succeeded(), insert::toString);

        StressResult validate = stress.run(
                "user",
                "profile=" + profile,
                "ops(validate=1)",
                "no-warmup",
                "n=300",
                "-pop",
                "seq=1..100",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast");
        assertTrue(validate.succeeded(), validate::toString);
        assertEquals(0L, validate.totalErrors().orElseThrow(), validate::toString);
    }
}
