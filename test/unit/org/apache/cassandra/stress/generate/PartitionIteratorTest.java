package org.apache.cassandra.stress.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.apache.cassandra.stress.generate.values.Bytes;
import org.apache.cassandra.stress.generate.values.Generator;
import org.apache.cassandra.stress.generate.values.GeneratorConfig;
import org.apache.cassandra.stress.generate.values.Integers;
import org.apache.cassandra.stress.generate.values.Strings;
import org.apache.cassandra.stress.settings.OptionDistribution;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PartitionIteratorTest {
    private static GeneratorConfig config(String name, String clustering, String size) {
        return new GeneratorConfig(
                name,
                clustering == null ? null : OptionDistribution.get(clustering),
                size == null ? null : OptionDistribution.get(size),
                null);
    }

    private static PartitionGenerator generator(PartitionGenerator.Order order, boolean clustered) {
        List<Generator> key = List.of(new Strings("pk", config("pk", null, "fixed(8)")));
        List<Generator> clustering = clustered
                ? List.of(
                        new Integers("ck1", config("ck1", "fixed(3)", null)),
                        new Strings("ck2", config("ck2", "fixed(4)", "fixed(6)")))
                : List.of();
        List<Generator> values = List.of(new Bytes("v", config("v", null, "fixed(16)")));
        return new PartitionGenerator(key, clustering, values, order);
    }

    private static PartitionIterator iterator(PartitionGenerator generator) {
        SeedManager seeds = new SeedManager(StressSettings.parse(new String[] {"write", "n=100"}));
        return PartitionIterator.get(generator, seeds);
    }

    private static List<String> rows(PartitionGenerator generator, long seed, boolean isWrite) {
        PartitionIterator iterator = iterator(generator);
        assertTrue(iterator.reset(new Seed(seed, 1), 1d, 1d, isWrite));
        List<String> rows = new ArrayList<>();
        while (iterator.hasNext()) rows.add(describe(generator, iterator.next()));
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static String describe(PartitionGenerator generator, Row row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.partitionKey.length; i++)
            sb.append(generator
                            .partitionKey
                            .get(i)
                            .type
                            .getString(((Generator<Object>) generator.partitionKey.get(i))
                                    .type.decompose(row.partitionKey[i])))
                    .append('|');
        List<Generator> columns = new ArrayList<>(generator.clusteringComponents);
        columns.addAll(generator.valueComponents);
        for (int i = 0; i < row.row.length; i++)
            sb.append(
                            row.row[i] == null
                                    ? "null"
                                    : ((Generator<Object>) columns.get(i))
                                            .type.getString(
                                                    ((Generator<Object>) columns.get(i)).type.decompose(row.row[i])))
                    .append('|');
        return sb.toString();
    }

    private static String sha256(List<String> rows) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(String.join("\n", rows).getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @EnumSource(
            value = PartitionGenerator.Order.class,
            names = {"SORTED", "ARBITRARY"})
    void sameSeedGivesTheSameRows(PartitionGenerator.Order order) {
        assertEquals(rows(generator(order, true), 42, true), rows(generator(order, true), 42, true));
    }

    @Test
    void shuffledOrderVisitsEveryRow() {
        assertEquals(
                12,
                rows(generator(PartitionGenerator.Order.SHUFFLED, true), 42, true)
                        .size());
    }

    @Test
    void differentSeedsGiveDifferentPartitions() {
        assertNotEquals(
                rows(generator(PartitionGenerator.Order.SORTED, true), 1, true),
                rows(generator(PartitionGenerator.Order.SORTED, true), 2, true));
    }

    @Test
    void clusteringProducesEveryCombination() {
        List<String> rows = rows(generator(PartitionGenerator.Order.SORTED, true), 7, true);
        assertEquals(12, rows.size());
        assertEquals(
                1,
                rows.stream()
                        .map(r -> r.substring(0, r.indexOf('|')))
                        .distinct()
                        .count());
    }

    @Test
    void sortedOrderSortsTheClusteringKeys() {
        PartitionGenerator generator = generator(PartitionGenerator.Order.SORTED, true);
        PartitionIterator iterator = iterator(generator);
        iterator.reset(new Seed(7, 1), 1d, 1d, true);
        List<Integer> firstComponent = new ArrayList<>();
        while (iterator.hasNext()) firstComponent.add((Integer) iterator.next().get(0));
        List<Integer> sorted = new ArrayList<>(firstComponent);
        sorted.sort(null);
        assertEquals(sorted, firstComponent);
    }

    @Test
    void singleRowPartitionsHaveOneRow() {
        List<String> rows = rows(generator(PartitionGenerator.Order.ARBITRARY, false), 5, true);
        assertEquals(1, rows.size());
        assertEquals(rows, rows(generator(PartitionGenerator.Order.ARBITRARY, false), 5, false));
    }

    @Test
    void generatedRowsMatchThePinnedDigest() throws Exception {
        List<String> rows = new ArrayList<>();
        for (long seed = 1; seed <= 20; seed++) {
            rows.addAll(rows(generator(PartitionGenerator.Order.SORTED, true), seed, true));
            rows.addAll(rows(generator(PartitionGenerator.Order.ARBITRARY, false), seed, true));
        }
        assertEquals(PINNED_DIGEST, sha256(rows));
    }

    @Test
    void boundsCoverTheRequestedDepth() {
        PartitionGenerator generator = generator(PartitionGenerator.Order.SORTED, true);
        PartitionIterator iterator = iterator(generator);
        Pair<Row, Row> bounds = iterator.resetToBounds(new Seed(3, 1), 1);
        assertEquals(1, bounds.left().row.length);
        assertFalse(bounds.left().row[0] == null);
    }

    @Test
    void generatorMapsColumnNamesToIndexes() {
        PartitionGenerator generator = generator(PartitionGenerator.Order.SORTED, true);
        assertEquals(List.of("pk", "ck1", "ck2", "v"), generator.getColumnNames());
        assertEquals(-1, generator.indexOf("pk"));
        assertEquals(0, generator.indexOf("ck1"));
        assertEquals(2, generator.indexOf("v"));
        assertEquals(12.0, generator.maxRowCount);
    }

    private static final String PINNED_DIGEST = "2a2dc32a40c782a9cb31857727f5b30b326645ad49564d135bc6a07a3e696df4";
}
