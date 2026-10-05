package org.apache.cassandra.stress.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.cassandra.stress.generate.values.Generator;
import org.apache.cassandra.stress.generate.values.GeneratorConfig;
import org.apache.cassandra.stress.generate.values.Integers;
import org.apache.cassandra.stress.settings.OptionDistribution;
import org.apache.cassandra.stress.settings.StressSettings;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PartitionWriteTest {
    private static PartitionGenerator generator(String shape, PartitionGenerator.Order order) {
        List<Generator> clustering = new ArrayList<>();
        String[] counts = shape.split("x");
        for (int i = 0; i < counts.length; i++)
            clustering.add(new Integers(
                    "c" + i,
                    new GeneratorConfig("c" + i, OptionDistribution.get("fixed(" + counts[i] + ")"), null, null)));
        return new PartitionGenerator(
                List.of(new Integers("pk", new GeneratorConfig("pk", null, null, null))),
                clustering,
                List.of(new Integers("v", new GeneratorConfig("v", null, null, null))),
                order);
    }

    private static PartitionIterator iterator(PartitionGenerator generator) {
        return PartitionIterator.get(generator, new SeedManager(StressSettings.parse(new String[] {"write", "n=100"})));
    }

    private static Set<String> rows(PartitionIterator iterator) {
        Set<String> rows = new TreeSet<>();
        while (iterator.hasNext()) rows.add(Arrays.deepToString(new Object[] {iterator.next().row}));
        return rows;
    }

    private static Set<String> read(PartitionGenerator generator, long seed) {
        PartitionIterator iterator = iterator(generator);
        iterator.reset(new Seed(seed, 1), 1d, 1d, false);
        return rows(iterator);
    }

    @ParameterizedTest
    @CsvSource({"3, 3", "3x2, 6", "3x2x2, 12", "2x3x2x2, 24", "4x1x3, 12"})
    void aSingleVisitWritesTheWholePartition(String shape, int rowCount) {
        for (PartitionGenerator.Order order :
                new PartitionGenerator.Order[] {PartitionGenerator.Order.ARBITRARY, PartitionGenerator.Order.SORTED}) {
            PartitionGenerator generator = generator(shape, order);
            for (long seed = 1; seed <= 10; seed++) {
                PartitionIterator iterator = iterator(generator);
                iterator.reset(new Seed(seed, 1), 1d, 1d, true);
                Set<String> written = rows(iterator);
                assertEquals(rowCount, written.size(), shape + " " + order + " seed " + seed);
                assertEquals(read(generator, seed), written);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"3x2x2, 3", "2x3x2x2, 4", "4x1x3, 2"})
    void everyVisitTogetherWritesTheWholePartition(String shape, int visits) {
        PartitionGenerator generator = generator(shape, PartitionGenerator.Order.ARBITRARY);
        for (long value = 1; value <= 10; value++) {
            Seed seed = new Seed(value, visits);
            Set<String> written = new TreeSet<>();
            for (int visit = 0; visit < visits; visit++) {
                PartitionIterator iterator = iterator(generator);
                if (iterator.reset(seed, 1d, 1d, true)) written.addAll(rows(iterator));
            }
            assertEquals(read(generator, value), written, shape + " seed " + value);
        }
    }
}
