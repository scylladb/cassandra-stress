package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserProfileIT {
    @TempDir
    Path dir;

    @Test
    void insertsAndRunsTheProfileQueries() {
        ScyllaNode.dropKeyspace("stresscql");
        CassandraStress stress = new CassandraStress(dir);

        StressResult insert = stress.run(
                "user",
                "profile=" + CassandraStress.profile("cqlstress-example.yaml"),
                "ops(insert=1)",
                "no-warmup",
                "n=500",
                "cl=QUORUM",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast");
        assertTrue(insert.succeeded(), insert::toString);

        StressResult queries = stress.run(
                "user",
                "profile=" + CassandraStress.profile("cqlstress-example.yaml"),
                "ops(simple1=1,range1=1)",
                "no-warmup",
                "n=200",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast");
        assertTrue(queries.succeeded(), queries::toString);
        assertEquals(0L, queries.totalErrors().orElseThrow());
    }

    @Test
    void sweepsTokenRanges() {
        ScyllaNode.dropKeyspace("stresscql");
        CassandraStress stress = new CassandraStress(dir);

        assertTrue(stress.run(
                        "user",
                        "profile=" + CassandraStress.profile("cqlstress-example.yaml"),
                        "ops(insert=1)",
                        "no-warmup",
                        "n=200",
                        "-rate",
                        "threads=2")
                .succeeded());

        StressResult sweep = stress.run(
                "user",
                "profile=" + CassandraStress.profile("cqlstress-example.yaml"),
                "ops(all_columns_tr_query=1)",
                "no-warmup",
                "n=20",
                "-rate",
                "threads=1",
                "-errors",
                "fail-fast");
        assertTrue(sweep.succeeded(), sweep::toString);
    }

    @Test
    void runsTwoProfilesAtOnce() {
        ScyllaNode.dropKeyspace("stresscql");
        CassandraStress stress = new CassandraStress(dir);

        StressResult result = stress.run(
                "user",
                "ops(alpha_workload.insert=1,beta_workload.insert=1)",
                "profile=" + CassandraStress.profile("cqlstress-example-specA.yaml") + ","
                        + CassandraStress.profile("cqlstress-counter-example-specB.yaml"),
                "no-warmup",
                "n=400",
                "cl=QUORUM",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast");
        assertTrue(result.succeeded(), result::toString);
    }

    @Test
    void validatesTheRowsItInserted() {
        ScyllaNode.dropKeyspace("stresscql");
        CassandraStress stress = new CassandraStress(dir);

        assertTrue(stress.run(
                        "user",
                        "profile=" + CassandraStress.profile("cqlstress-counter-example.yaml"),
                        "ops(insert=1)",
                        "no-warmup",
                        "n=300",
                        "-rate",
                        "threads=2")
                .succeeded());
        StressResult read = stress.run(
                "user",
                "profile=" + CassandraStress.profile("cqlstress-counter-example.yaml"),
                "ops(simple1=1)",
                "no-warmup",
                "n=100",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast");
        assertTrue(read.succeeded(), read::toString);
    }

    @SuppressWarnings("InlineFormatString")
    private static final String FROZEN_CLUSTERING = """
        keyspace: %1$s
        keyspace_definition: |
          CREATE KEYSPACE %1$s WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 1};
        table: rows
        table_definition: |
          CREATE TABLE rows (pk bigint, ck %2$s, v text, PRIMARY KEY (pk, ck))
            WITH CLUSTERING ORDER BY (ck %3$s)
        columnspec:
          - name: ck
            cluster: fixed(8)
            size: uniform(1..4)
        insert:
          partitions: fixed(1)
          select: fixed(1)/1
          batchtype: UNLOGGED
        queries:
          bykey:
            cql: select * from rows where pk = ?
        """;

    @ParameterizedTest
    @CsvSource({
        "frozen_list_asc, frozen<list<int>>, ASC",
        "frozen_list_desc, frozen<list<int>>, DESC",
        "frozen_set_asc, frozen<set<text>>, ASC",
        "frozen_set_desc, frozen<set<text>>, DESC",
    })
    void validatesAndReadsFrozenCollectionClusteringColumns(String keyspace, String type, String order)
            throws IOException {
        ScyllaNode.dropKeyspace(keyspace);
        Path profile =
                Files.writeString(dir.resolve(keyspace + ".yaml"), FROZEN_CLUSTERING.formatted(keyspace, type, order));
        CassandraStress stress = new CassandraStress(dir);

        for (String operation : List.of("insert", "validate", "bykey")) {
            StressResult result = stress.run(
                    "user",
                    "profile=" + profile,
                    "ops(" + operation + "=1)",
                    "no-warmup",
                    "n=50",
                    "-pop",
                    "seq=1..50",
                    "-rate",
                    "threads=1",
                    "-errors",
                    "fail-fast");
            assertTrue(result.succeeded(), () -> operation + ": " + result);
            assertTrue(result.totalPartitions().orElseThrow() > 0, () -> operation + ": " + result);
        }
    }

    private static final String RESERVED_NAMES = """
        keyspace: MixedCase
        keyspace_definition: |
          CREATE KEYSPACE MixedCase WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 1};
        table: Reserved
        table_definition: |
          CREATE TABLE Reserved (pk bigint, "order" int, "token" text, PRIMARY KEY (pk, "order"))
            WITH CLUSTERING ORDER BY ("order" DESC)
        columnspec:
          - name: order
            cluster: fixed(5)
        insert:
          partitions: fixed(1)
          select: fixed(1)/1
          batchtype: UNLOGGED
        queries:
          bykey:
            cql: select * from Reserved where pk = ?
        token_range_queries:
          everything:
            columns: '*'
        """;

    @Test
    void runsAProfileWithMixedCaseAndReservedNames() throws IOException {
        ScyllaNode.dropKeyspace("mixedcase");
        Path profile = Files.writeString(dir.resolve("reserved.yaml"), RESERVED_NAMES);
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
                "threads=2",
                "-errors",
                "fail-fast");
        assertTrue(insert.succeeded(), insert::toString);
        assertEquals(500L, ScyllaNode.count("mixedcase", "reserved"));

        StressResult validate = stress.run(
                "user",
                "profile=" + profile,
                "ops(validate=1,bykey=1)",
                "no-warmup",
                "n=200",
                "-pop",
                "seq=1..100",
                "-rate",
                "threads=2",
                "-errors",
                "fail-fast");
        assertTrue(validate.succeeded(), validate::toString);
        assertEquals(0L, validate.totalErrors().orElseThrow(), validate::toString);

        StressResult sweep = stress.run(
                "user",
                "profile=" + profile,
                "ops(everything=1)",
                "no-warmup",
                "n=10",
                "-rate",
                "threads=1",
                "-errors",
                "fail-fast");
        assertTrue(sweep.succeeded(), sweep::toString);
    }
}
