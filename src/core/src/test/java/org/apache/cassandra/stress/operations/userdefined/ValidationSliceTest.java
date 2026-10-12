package org.apache.cassandra.stress.operations.userdefined;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.cassandra.stress.Operation;
import org.apache.cassandra.stress.WorkManager;
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.CqlType;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.TableSchema;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.PartitionIterator;
import org.apache.cassandra.stress.generate.Row;
import org.apache.cassandra.stress.generate.Seed;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.generate.values.GeneratorConfig;
import org.apache.cassandra.stress.generate.values.Integers;
import org.apache.cassandra.stress.generate.values.Strings;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.OptionDistribution;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.Pair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidationSliceTest {
    private static final StressSettings SETTINGS = StressSettings.parse(new String[] {"write", "n=100"});

    private static GeneratorConfig config(String name, String clustering, String population) {
        return new GeneratorConfig(
                name,
                clustering == null ? null : OptionDistribution.get(clustering),
                null,
                population == null ? null : OptionDistribution.get(population));
    }

    private static PartitionGenerator generator(boolean descending) {
        return new PartitionGenerator(
                List.of(new Strings("pk", config("pk", null, null))),
                List.of(
                        new Integers("a", config("a", "fixed(5)", "uniform(1..50)")),
                        new Integers("b", config("b", "fixed(4)", "uniform(1..50)"))),
                List.of(new Integers("v", config("v", null, null))),
                PartitionGenerator.Order.ARBITRARY,
                new boolean[] {descending, descending});
    }

    private static TableSchema table(boolean descending) {
        return new TableSchema(
                "ks",
                "t",
                List.of(new ColumnSchema("pk", CqlType.of("TEXT"))),
                List.of(
                        new ColumnSchema("a", CqlType.of("INT"), descending),
                        new ColumnSchema("b", CqlType.of("INT"), descending)),
                List.of(new ColumnSchema("v", CqlType.of("INT"))));
    }

    private static final class Reader extends Operation {
        Reader() {
            super(new Timer("read", (opType, intended, started, ended, rows, partitions, error) -> {}), SETTINGS);
        }

        @Override
        public int ready(WorkManager permits) {
            return 0;
        }

        @Override
        public void run(StressClient client) {}

        @Override
        public String key() {
            return "";
        }
    }

    private static int compare(Object[] row, Object[] bound) {
        for (int i = 0; i < bound.length; i++) {
            int c = Integer.compare((Integer) row[i], (Integer) bound[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static boolean selected(
            Object[] row, Object[] lo, Object[] hi, ValidatingSchemaQuery.Slice slice, boolean desc) {
        int fromLo = compare(row, lo);
        int fromHi = compare(row, hi);
        if (desc) {
            fromLo = -fromLo;
            fromHi = -fromHi;
        }
        boolean afterStart = slice.inclusiveStart() ? fromLo >= 0 : fromLo > 0;
        boolean beforeEnd = slice.inclusiveEnd() ? fromHi <= 0 : fromHi < 0;
        return afterStart && beforeEnd;
    }

    private static List<List<Object>> values(List<Row> rows) {
        return rows.stream().map(r -> Arrays.asList(r.row.clone())).toList();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eachSliceExpectsTheRowsThatItsCqlSelects(boolean descending) {
        PartitionGenerator generator = generator(descending);
        SeedManager seeds = new SeedManager(SETTINGS);
        PartitionIterator all = PartitionIterator.get(generator, seeds);
        PartitionIterator slice = PartitionIterator.get(generator, seeds);
        List<List<ValidatingSchemaQuery.Slice>> queries = ValidatingSchemaQuery.queries(table(descending));
        Reader reader = new Reader();
        int checked = 0;

        for (int i = 0; i < 200; i++) {
            Seed seed = seeds.next(reader);
            all.resetToBounds(seed, 0);
            List<Row> partition = new ArrayList<>();
            while (all.hasNext()) {
                partition.add(all.next().copy());
            }

            for (int depth = 1; depth < queries.size(); depth++) {
                for (ValidatingSchemaQuery.Slice cql : queries.get(depth)) {
                    Pair<Row, Row> bounds = slice.resetToBounds(seed, depth);
                    List<Row> expected = new ArrayList<>();
                    for (Row row :
                            ValidatingSchemaQuery.expectedRows(slice, cql.inclusiveStart(), cql.inclusiveEnd())) {
                        expected.add(row.copy());
                    }
                    List<Row> selectedByCql = partition.stream()
                            .filter(row -> selected(row.row, bounds.left().row, bounds.right().row, cql, descending))
                            .toList();
                    assertEquals(values(selectedByCql), values(expected), cql.cql());
                    checked++;
                }
            }
        }
        assertFalse(checked == 0);
    }
}
