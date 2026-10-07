package org.apache.cassandra.stress.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.apache.cassandra.stress.generate.values.Bytes;
import org.apache.cassandra.stress.generate.values.Generator;
import org.apache.cassandra.stress.generate.values.GeneratorConfig;
import org.apache.cassandra.stress.generate.values.Inets;
import org.apache.cassandra.stress.generate.values.Strings;
import org.apache.cassandra.stress.generate.values.TimeUUIDs;
import org.apache.cassandra.stress.generate.values.UUIDs;
import org.apache.cassandra.stress.settings.OptionDistribution;
import org.apache.cassandra.stress.settings.StressSettings;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeneratedDataCompatibilityTest {
    private static GeneratorConfig config(String name, String clustering, String size) {
        return new GeneratorConfig(
                name,
                clustering == null ? null : OptionDistribution.get(clustering),
                size == null ? null : OptionDistribution.get(size),
                null);
    }

    private static PartitionGenerator generator(PartitionGenerator.Order order) {
        return new PartitionGenerator(
                List.of(new Strings("pk", config("pk", null, "fixed(8)"))),
                List.of(
                        new Bytes("c1", config("c1", "fixed(3)", "fixed(4)")),
                        new TimeUUIDs("c2", config("c2", "fixed(2)", null)),
                        new UUIDs("c3", config("c3", "fixed(2)", null)),
                        new Inets("c4", config("c4", "fixed(2)", null))),
                List.of(new Strings("v", config("v", null, "fixed(10)"))),
                order);
    }

    @SuppressWarnings("unchecked")
    private static String describe(PartitionGenerator generator, Row row) {
        List<Generator> columns = new ArrayList<>(generator.partitionKey);
        columns.addAll(generator.clusteringComponents);
        columns.addAll(generator.valueComponents);
        Object[] values = new Object[row.partitionKey.length + row.row.length];
        System.arraycopy(row.partitionKey, 0, values, 0, row.partitionKey.length);
        System.arraycopy(row.row, 0, values, row.partitionKey.length, row.row.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) sb.append("null");
            else {
                ByteBuffer bytes = ((Generator<Object>) columns.get(i)).type.decompose(values[i]);
                byte[] array = new byte[bytes.remaining()];
                bytes.duplicate().get(array);
                sb.append(HexFormat.of().formatHex(array));
            }
            sb.append('|');
        }
        return sb.toString();
    }

    private static List<List<String>> partitions(PartitionGenerator.Order order, boolean isWrite) {
        PartitionGenerator generator = generator(order);
        SeedManager seeds = new SeedManager(StressSettings.parse(new String[] {"write", "n=100"}));
        List<List<String>> partitions = new ArrayList<>();
        for (long seed = 1; seed <= 15; seed++) {
            PartitionIterator iterator = PartitionIterator.get(generator, seeds);
            iterator.reset(new Seed(seed, 1), 1d, 1d, isWrite);
            List<String> rows = new ArrayList<>();
            while (iterator.hasNext()) rows.add(describe(generator, iterator.next()));
            partitions.add(rows);
        }
        return partitions;
    }

    private static String sha256(List<String> rows) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(String.join("\n", rows).getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @CsvSource({
        "ARBITRARY, 9423ff51a324c2deb90dda83c81f346653f2d60d1f2c7d4ae92dacca4107fe01",
        "SORTED, 9c815900b0fdd970c15d0fd4d875bd21b8ede5501dcbd205c634e6aeaf6d1b60",
    })
    void readsTheRowsOfThePreviousRelease(PartitionGenerator.Order order, String digest) throws Exception {
        List<String> rows =
                partitions(order, false).stream().flatMap(List::stream).toList();
        assertEquals(360, rows.size());
        assertEquals(digest, sha256(rows));
    }

    @ParameterizedTest
    @CsvSource({
        "ARBITRARY, a60de66c836943e1575412c0cad19317ddcbf96f4f0acc6c4bfb801c68c40f9f",
        "SORTED, c1be7d5760d7d64cbbf115868a35a7b74a2ea4a149e57f47b00224b3eec904d3",
    })
    void writesTheRowsOfThePreviousReleaseFirst(PartitionGenerator.Order order, String digest) throws Exception {
        List<String> firstRows = partitions(order, true).stream()
                .flatMap(rows -> rows.subList(0, 2).stream())
                .toList();
        assertEquals(digest, sha256(firstRows));
    }

    @ParameterizedTest
    @CsvSource({"ARBITRARY", "SORTED"})
    void writesEveryRowThatItReads(PartitionGenerator.Order order) {
        assertEquals(partitions(order, false), partitions(order, true));
    }
}
