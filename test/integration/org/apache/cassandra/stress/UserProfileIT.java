package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
