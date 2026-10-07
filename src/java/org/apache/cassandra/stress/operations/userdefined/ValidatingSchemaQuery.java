// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.StressBoundStatement;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressPreparedStatement;
import org.apache.cassandra.stress.driver.StressResult;
import org.apache.cassandra.stress.driver.TableSchema;
import org.apache.cassandra.stress.generate.DistributionFixed;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.PartitionIterator;
import org.apache.cassandra.stress.generate.Row;
import org.apache.cassandra.stress.generate.Seed;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.operations.PartitionOperation;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.CqlNames;
import org.apache.cassandra.stress.util.Pair;

public final class ValidatingSchemaQuery extends PartitionOperation {
    private Pair<Row, Row> bounds;

    final int clusteringComponents;
    final ValidatingStatement[] statements;
    final ConsistencyLevel cl;
    final Object[] bindBuffer;

    private ValidatingSchemaQuery(
            Timer timer,
            StressSettings settings,
            PartitionGenerator generator,
            SeedManager seedManager,
            ValidatingStatement[] statements,
            ConsistencyLevel cl,
            ConsistencyLevel serialCl,
            int clusteringComponents) {
        super(
                timer,
                settings,
                new DataSpec(
                        generator, seedManager, new DistributionFixed(1), settings.insert.rowPopulationRatio.get(), 1));
        this.statements = statements;
        this.cl = cl;
        bindBuffer = new Object[statements[0].statement.variableCount()];
        for (ValidatingStatement statement : statements) {
            if (statement.statement.getConsistencyLevel() == null) {
                statement.statement.setConsistencyLevel(cl);
            }
            if (statement.statement.getSerialConsistencyLevel() == null) {
                statement.statement.setSerialConsistencyLevel(serialCl);
            }
        }
        this.clusteringComponents = clusteringComponents;
    }

    @Override
    protected boolean reset(Seed seed, PartitionIterator iterator) {
        if (isBeingWritten(seed)) {
            return false;
        }
        bounds = iterator.resetToBounds(seed, clusteringComponents);
        return true;
    }

    static List<Row> expectedRows(PartitionIterator iter, boolean inclusiveStart, boolean inclusiveEnd) {
        List<Row> rows = new ArrayList<>();
        if (!inclusiveStart && iter.hasNext()) {
            iter.next();
        }
        while (iter.hasNext()) {
            Row row = iter.next();
            if (!inclusiveEnd && !iter.hasNext()) {
                break;
            }
            rows.add(row);
        }
        return rows;
    }

    private static final int UNKNOWN_COLUMN = Integer.MIN_VALUE;

    int indexOf(String column) {
        return spec.partitionGenerator.contains(column) ? spec.partitionGenerator.indexOf(column) : UNKNOWN_COLUMN;
    }

    abstract class Runner implements RunOp {
        int partitionCount;
        int rowCount;
        String validationError;
        final PartitionIterator iter;
        final int statementIndex;
        private List<Row> expected;

        List<Row> expectedRows() {
            if (expected == null) {
                expected = ValidatingSchemaQuery.expectedRows(
                        iter, statements[statementIndex].inclusiveStart, statements[statementIndex].inclusiveEnd);
            }
            return expected;
        }

        protected Runner(PartitionIterator iter) {
            this.iter = iter;
            statementIndex = ThreadLocalRandom.current().nextInt(statements.length);
        }

        @Override
        public int partitionCount() {
            return partitionCount;
        }

        @Override
        public int rowCount() {
            return rowCount;
        }

        @Override
        public String validationErrorMessage() {
            return validationError;
        }
    }

    private final class JavaDriverRun extends Runner {
        final StressClient client;

        private JavaDriverRun(StressClient client, PartitionIterator iter) {
            super(iter);
            this.client = client;
        }

        @Override
        public boolean run() throws Exception {
            StressResult rs = client.execute(bind(statementIndex), null, null);
            List<String> columnNames = rs.columnNames();
            int[] valueIndex = new int[columnNames.size()];
            for (int i = 0; i < valueIndex.length; i++) {
                valueIndex[i] = indexOf(columnNames.get(i));
            }

            rowCount = 0;
            Iterator<ByteBuffer[]> results = rs.rows().iterator();
            for (Row expectedRow : expectedRows()) {
                if (!results.hasNext()) {
                    validationError = String.format(
                            Locale.ROOT,
                            "Data returned was not validated: expected row %d but result set exhausted (row"
                                    + " empty/missing)",
                            rowCount + 1);
                    return false;
                }

                rowCount++;
                ByteBuffer[] actualRow = results.next();
                for (int i = 0; i < actualRow.length; i++) {
                    if (valueIndex[i] == UNKNOWN_COLUMN) {
                        continue;
                    }
                    Object expectedValue = expectedRow.get(valueIndex[i]);
                    Object actualValue = spec.partitionGenerator.convert(valueIndex[i], actualRow[i]);
                    if (!Objects.equals(expectedValue, actualValue)) {
                        validationError = String.format(
                                Locale.ROOT,
                                "Data returned was not validated: row %d, column %d (%s): value mismatch"
                                        + " (expected [%s] %s, got [%s] %s)",
                                rowCount,
                                i,
                                columnNames.get(i),
                                expectedValue == null
                                        ? "null"
                                        : expectedValue.getClass().getSimpleName(),
                                expectedValue == null ? "null" : describeValue(expectedValue),
                                actualValue == null
                                        ? "null"
                                        : actualValue.getClass().getSimpleName(),
                                actualValue == null ? "null" : describeValue(actualValue));
                        return false;
                    }
                }
            }
            partitionCount = Math.min(1, rowCount);
            if (results.hasNext()) {
                validationError = String.format(
                        Locale.ROOT,
                        "Data returned was not validated: result set not exhausted after consuming %d expected row(s)"
                                + " (got more rows than expected)",
                        rowCount);
                return false;
            }
            return true;
        }
    }

    StressBoundStatement bind(int statementIndex) {
        int pkc = bounds.left().partitionKey.length;
        System.arraycopy(bounds.left().partitionKey, 0, bindBuffer, 0, pkc);
        int ccc = bounds.left().row.length;
        System.arraycopy(bounds.left().row, 0, bindBuffer, pkc, ccc);
        System.arraycopy(bounds.right().row, 0, bindBuffer, pkc + ccc, ccc);
        return statements[statementIndex].statement.bind(bindBuffer);
    }

    @Override
    public void run(StressClient client) throws IOException {
        timeWithRetry(new JavaDriverRun(client, partitions.getFirst()));
    }

    public static class Factory {
        final ValidatingStatement[] statements;
        final int clusteringComponents;

        public Factory(ValidatingStatement[] statements, int clusteringComponents) {
            this.statements = statements;
            this.clusteringComponents = clusteringComponents;
        }

        public ValidatingSchemaQuery create(
                Timer timer,
                StressSettings settings,
                PartitionGenerator generator,
                SeedManager seedManager,
                ConsistencyLevel cl,
                ConsistencyLevel serialCl) {
            return new ValidatingSchemaQuery(
                    timer, settings, generator, seedManager, statements, cl, serialCl, clusteringComponents);
        }
    }

    record Slice(String cql, boolean inclusiveStart, boolean inclusiveEnd) {}

    public static List<Factory> create(TableSchema metadata, StressSettings settings) {
        List<Factory> factories = new ArrayList<>();
        List<List<Slice>> queries = queries(metadata);
        for (int depth = 0; depth < queries.size(); depth++) {
            List<Slice> slices = queries.get(depth);
            ValidatingStatement[] statements = new ValidatingStatement[slices.size()];
            for (int i = 0; i < statements.length; i++) {
                statements[i] = prepare(
                        settings,
                        slices.get(i).cql(),
                        slices.get(i).inclusiveStart(),
                        slices.get(i).inclusiveEnd());
            }
            factories.add(new Factory(statements, depth));
        }
        return factories;
    }

    static List<List<Slice>> queries(TableSchema metadata) {
        StringBuilder sb = new StringBuilder("SELECT * FROM ")
                .append(CqlNames.quote(metadata.name()))
                .append(" WHERE");
        boolean first = true;
        for (ColumnSchema column : metadata.partitionKey()) {
            sb.append(first ? " " : " AND ")
                    .append(CqlNames.quote(column.name()))
                    .append(" = ?");
            first = false;
        }
        String base = sb.toString();

        List<List<Slice>> queries = new ArrayList<>();
        queries.add(List.of(new Slice(base, true, true)));

        List<String> names = new ArrayList<>();
        List<Boolean> orders = new ArrayList<>();
        for (ColumnSchema column : metadata.clusteringColumns()) {
            names.add(CqlNames.quote(column.name()));
            orders.add(column.descending());
        }

        int maxDepth = names.size() - 1;
        for (int depth = 0; depth <= maxDepth && orders.get(depth).equals(orders.getFirst()); depth++) {
            boolean descending = orders.getFirst();
            String columns = "(" + String.join(",", names.subList(0, depth + 1)) + ")";
            String values = "(" + String.join(",", Collections.nCopies(depth + 1, "?")) + ")";
            List<Slice> slices = new ArrayList<>();
            for (boolean incLb : depth < maxDepth ? new boolean[] {true} : new boolean[] {true, false}) {
                for (boolean incUb : depth < maxDepth ? new boolean[] {false} : new boolean[] {true, false}) {
                    String lb = descending ? (incLb ? "<=" : "<") : (incLb ? ">=" : ">");
                    String ub = descending ? (incUb ? ">=" : ">") : (incUb ? "<=" : "<");
                    slices.add(new Slice(
                            base + " AND " + columns + lb + values + " AND " + columns + ub + values, incLb, incUb));
                }
            }
            queries.add(List.copyOf(slices));
        }
        return queries;
    }

    private static final class ValidatingStatement {
        final StressPreparedStatement statement;
        final boolean inclusiveStart;
        final boolean inclusiveEnd;

        private ValidatingStatement(StressPreparedStatement statement, boolean inclusiveStart, boolean inclusiveEnd) {
            this.statement = statement;
            this.inclusiveStart = inclusiveStart;
            this.inclusiveEnd = inclusiveEnd;
        }
    }

    private static ValidatingStatement prepare(StressSettings settings, String cql, boolean incLb, boolean incUb) {
        return new ValidatingStatement(settings.getClient().prepare(cql), incLb, incUb);
    }

    private static String describeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof ByteBuffer bb) {
            return hexPreview(bb, 16) + " (" + bb.remaining() + " bytes)";
        }
        if (value instanceof byte[] b) {
            return hexPreview(ByteBuffer.wrap(b), 16) + " (" + b.length + " bytes)";
        }
        String s = String.valueOf(value);
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
