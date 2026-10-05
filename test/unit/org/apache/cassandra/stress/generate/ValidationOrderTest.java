package org.apache.cassandra.stress.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.apache.cassandra.stress.generate.values.Bytes;
import org.apache.cassandra.stress.generate.values.Generator;
import org.apache.cassandra.stress.generate.values.GeneratorConfig;
import org.apache.cassandra.stress.generate.values.Inets;
import org.apache.cassandra.stress.generate.values.Integers;
import org.apache.cassandra.stress.generate.values.LocalDates;
import org.apache.cassandra.stress.generate.values.Strings;
import org.apache.cassandra.stress.generate.values.TimeUUIDs;
import org.apache.cassandra.stress.settings.OptionDistribution;
import org.apache.cassandra.stress.settings.StressSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ValidationOrderTest {
    private static GeneratorConfig config(String name, String clustering, String size, String population) {
        return new GeneratorConfig(
                name,
                clustering == null ? null : OptionDistribution.get(clustering),
                size == null ? null : OptionDistribution.get(size),
                population == null ? null : OptionDistribution.get(population));
    }

    private static PartitionGenerator generator(
            Generator clustering, PartitionGenerator.Order order, boolean descending) {
        return new PartitionGenerator(
                List.of(new Strings("pk", config("pk", null, "fixed(8)", null))),
                List.of(clustering),
                List.of(new Integers("v", config("v", null, null, null))),
                order,
                new boolean[] {descending});
    }

    private static List<Row> validated(PartitionGenerator generator, long seed) {
        PartitionIterator iterator = PartitionIterator.get(
                generator, new SeedManager(StressSettings.parse(new String[] {"write", "n=100"})));
        iterator.resetToBounds(new Seed(seed, 1), 0);
        List<Row> rows = new ArrayList<>();
        while (iterator.hasNext()) rows.add(iterator.next());
        return rows;
    }

    private static List<Row> written(PartitionGenerator generator, long seed) {
        PartitionIterator iterator = PartitionIterator.get(
                generator, new SeedManager(StressSettings.parse(new String[] {"write", "n=100"})));
        iterator.reset(new Seed(seed, 1), 1d, 1d, false);
        List<Row> rows = new ArrayList<>();
        while (iterator.hasNext()) rows.add(iterator.next());
        return rows;
    }

    private static <T> void assertOrdered(List<Row> rows, Comparator<T> stored) {
        assertTrue(rows.size() > 1);
        for (int i = 1; i < rows.size(); i++) {
            @SuppressWarnings("unchecked")
            T previous = (T) rows.get(i - 1).row[0];
            @SuppressWarnings("unchecked")
            T current = (T) rows.get(i).row[0];
            assertTrue(stored.compare(previous, current) < 0, previous + " before " + current);
        }
    }

    private static int unsigned(ByteBuffer left, ByteBuffer right) {
        byte[] l = new byte[left.remaining()];
        byte[] r = new byte[right.remaining()];
        left.duplicate().get(l);
        right.duplicate().get(r);
        return Arrays.compareUnsigned(l, r);
    }

    private static List<String> describe(List<Row> rows) {
        List<String> out = new ArrayList<>();
        for (Row row : rows) out.add(Arrays.deepToString(new Object[] {row.partitionKey, row.row}));
        out.sort(Comparator.naturalOrder());
        return out;
    }

    @Test
    void blobClusteringFollowsUnsignedByteOrder() {
        PartitionGenerator generator = generator(
                new Bytes("c", config("c", "fixed(40)", "fixed(2)", null)), PartitionGenerator.Order.ARBITRARY, false);
        for (long seed = 1; seed <= 20; seed++)
            assertOrdered(validated(generator, seed), ValidationOrderTest::unsigned);
    }

    @Test
    void timeuuidClusteringFollowsTheTimestamp() {
        PartitionGenerator generator = generator(
                new TimeUUIDs("c", config("c", "fixed(20)", null, null)), PartitionGenerator.Order.ARBITRARY, false);
        for (long seed = 1; seed <= 20; seed++)
            assertOrdered(validated(generator, seed), Comparator.comparingLong(UUID::timestamp));
    }

    @Test
    void dateClusteringFollowsTheDay() {
        PartitionGenerator generator = generator(
                new LocalDates("c", config("c", "fixed(20)", null, "uniform(-1000..1000)")),
                PartitionGenerator.Order.ARBITRARY,
                false);
        for (long seed = 1; seed <= 20; seed++)
            assertOrdered(validated(generator, seed), Comparator.<Integer>naturalOrder());
    }

    @Test
    void descendingClusteringIsReversed() {
        PartitionGenerator generator = generator(
                new Bytes("c", config("c", "fixed(40)", "fixed(2)", null)), PartitionGenerator.Order.ARBITRARY, true);
        for (long seed = 1; seed <= 20; seed++)
            assertOrdered(validated(generator, seed), (ByteBuffer l, ByteBuffer r) -> unsigned(r, l));
    }

    @ParameterizedTest
    @EnumSource(
            value = PartitionGenerator.Order.class,
            names = {"ARBITRARY", "SORTED"})
    void validationRegeneratesTheWrittenRowsForEveryClusteringType(PartitionGenerator.Order order) {
        List<Generator> clustering = List.of(
                new Bytes("c", config("c", "fixed(30)", "fixed(2)", null)),
                new TimeUUIDs("c", config("c", "fixed(30)", null, null)),
                new Inets("c", config("c", "fixed(30)", null, null)),
                new LocalDates("c", config("c", "fixed(30)", null, "uniform(-1000..1000)")));
        for (Generator column : clustering) {
            for (boolean descending : new boolean[] {false, true}) {
                PartitionGenerator generator = generator(column, order, descending);
                for (long seed = 1; seed <= 10; seed++)
                    assertEquals(
                            describe(written(generator, seed)),
                            describe(validated(generator, seed)),
                            column.getClass().getSimpleName() + " descending=" + descending);
            }
        }
    }
}
