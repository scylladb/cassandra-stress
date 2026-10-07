// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.predefined;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressPreparedStatement;
import org.apache.cassandra.stress.driver.StressResult;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.Command;
import org.apache.cassandra.stress.settings.ConnectionStyle;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public abstract class CqlOperation<V> extends PredefinedOperation {

    public static final ByteBuffer[][] EMPTY_BYTE_BUFFERS = new ByteBuffer[0][];

    protected abstract List<Object> getQueryParameters(byte[] key);

    protected abstract String buildQuery();

    protected abstract CqlRunOp<V> buildRunOp(
            ClientWrapper client, String query, Object queryId, List<Object> params, ByteBuffer key);

    public CqlOperation(
            Command type, Timer timer, PartitionGenerator generator, SeedManager seedManager, StressSettings settings) {
        super(type, timer, generator, seedManager, settings);
        if (settings.columns.variableColumnCount)
            throw new IllegalStateException("Variable column counts are not implemented for CQL");
    }

    protected CqlRunOp<V> run(final ClientWrapper client, final List<Object> queryParams, final ByteBuffer key)
            throws IOException {
        final CqlRunOp<V> op;
        if (settings.mode.style == ConnectionStyle.CQL_PREPARED) {
            final Object id;
            Object idobj = getCqlCache();
            if (idobj == null) {
                id = client.createPreparedStatement(buildQuery());
                storeCqlCache(id);
            } else id = idobj;

            op = buildRunOp(client, null, id, queryParams, key);
        } else {
            final String query;
            Object qobj = getCqlCache();
            if (qobj == null) {
                query = buildQuery();
                storeCqlCache(query);
            } else query = qobj.toString();

            op = buildRunOp(client, query, null, queryParams, key);
        }

        timeWithRetry(op);
        return op;
    }

    protected void run(final ClientWrapper client) throws IOException {
        final byte[] key = getKey().array();
        final List<Object> queryParams = getQueryParameters(key);
        run(client, queryParams, ByteBuffer.wrap(key));
    }

    protected static final class CqlRunOpAlwaysSucceed extends CqlRunOp<Integer> {

        final int keyCount;

        CqlRunOpAlwaysSucceed(
                ClientWrapper client, String query, Object queryId, List<Object> params, ByteBuffer key, int keyCount) {
            super(client, query, queryId, RowCountHandler.INSTANCE, params, key);
            this.keyCount = keyCount;
        }

        @Override
        public boolean validate(Integer result) {
            return true;
        }

        @Override
        public int partitionCount() {
            return keyCount;
        }

        @Override
        public int rowCount() {
            return keyCount;
        }
    }

    protected static final class CqlRunOpTestNonEmpty extends CqlRunOp<Integer> {

        CqlRunOpTestNonEmpty(ClientWrapper client, String query, Object queryId, List<Object> params, ByteBuffer key) {
            super(client, query, queryId, RowCountHandler.INSTANCE, params, key);
        }

        @Override
        public boolean validate(Integer result) {
            return result > 0;
        }

        @Override
        public int partitionCount() {
            return result;
        }

        @Override
        public int rowCount() {
            return result;
        }
    }

    protected final class CqlRunOpMatchResults extends CqlRunOp<ByteBuffer[][]> {

        final List<List<ByteBuffer>> expect;
        private String validationError;

        CqlRunOpMatchResults(
                ClientWrapper client,
                String query,
                Object queryId,
                List<Object> params,
                ByteBuffer key,
                List<List<ByteBuffer>> expect) {
            super(client, query, queryId, RowsHandler.INSTANCE, params, key);
            this.expect = expect;
        }

        @Override
        public int partitionCount() {
            return result == null ? 0 : result.length;
        }

        @Override
        public int rowCount() {
            return result == null ? 0 : result.length;
        }

        @Override
        public String validationErrorMessage() {
            return validationError;
        }

        @Override
        public boolean validate(ByteBuffer[][] result) {
            if (!settings.errors.skipReadValidation) {
                int expectedRows = expect.size();
                int actualRows = result.length;

                if (actualRows != expectedRows) {
                    long expectedBytes = 0;
                    int expectedColsPerRow = 0;
                    if (expectedRows > 0 && expect.getFirst() != null) {
                        expectedColsPerRow = expect.getFirst().size();
                        for (List<ByteBuffer> row : expect) if (row != null) expectedBytes += totalBytes(row);
                    }

                    if (actualRows == 0) {
                        validationError = String.format(
                                Locale.ROOT,
                                "Data returned was not validated: row empty/missing (expected %d row(s) with %d"
                                        + " column(s) %s, %d bytes total (%dx%d); got 0 rows)",
                                expectedRows,
                                expectedColsPerRow,
                                columnNamesPreview(expectedColsPerRow),
                                expectedBytes,
                                expectedColsPerRow,
                                expectedColsPerRow > 0 ? expectedBytes / expectedColsPerRow : 0);
                    } else {
                        long actualBytes = 0;
                        for (ByteBuffer[] row : result) if (row != null) actualBytes += totalBytes(row);
                        validationError = String.format(
                                Locale.ROOT,
                                "Data returned was not validated: row count mismatch"
                                        + " (expected %d row(s) / %d bytes total; got %d row(s) / %d bytes total)",
                                expectedRows,
                                expectedBytes,
                                actualRows,
                                actualBytes);
                    }
                    return false;
                }

                for (int i = 0; i < result.length; i++) {
                    List<ByteBuffer> expectedRow = expect.get(i);
                    if (expectedRow == null) continue;
                    ByteBuffer[] actualRow = result[i];

                    if (actualRow.length != expectedRow.size()) {
                        long expectedRowBytes = totalBytes(expectedRow);
                        long actualRowBytes = totalBytes(actualRow);
                        validationError = String.format(
                                Locale.ROOT,
                                "Data returned was not validated: row %d column count mismatch"
                                        + " (expected %d column(s) %s / %d bytes; got %d column(s) / %d bytes)",
                                i,
                                expectedRow.size(),
                                columnNamesPreview(expectedRow.size()),
                                expectedRowBytes,
                                actualRow.length,
                                actualRowBytes);
                        return false;
                    }

                    for (int j = 0; j < expectedRow.size(); j++) {
                        ByteBuffer expectedVal = expectedRow.get(j);
                        ByteBuffer actualVal = actualRow[j];
                        if (expectedVal != null && !expectedVal.equals(actualVal)) {
                            int expectedSize = expectedVal.remaining();
                            int actualSize = (actualVal != null) ? actualVal.remaining() : -1;
                            String colLabel = columnLabel(j);
                            String diff;
                            if (actualSize < 0)
                                diff = String.format(Locale.ROOT, "got null (expected %d bytes)", expectedSize);
                            else if (actualSize != expectedSize)
                                diff = String.format(
                                        Locale.ROOT, "expected %d bytes, got %d bytes", expectedSize, actualSize);
                            else
                                diff = String.format(
                                        Locale.ROOT,
                                        "same size (%d bytes) but content differs; expected[0..%d]=%s, got[0..%d]=%s",
                                        expectedSize,
                                        Math.min(15, expectedSize - 1),
                                        hexPreview(expectedVal, 16),
                                        Math.min(15, actualSize - 1),
                                        hexPreview(actualVal, 16));
                            validationError = String.format(
                                    Locale.ROOT, "Data returned was not validated: row %d, %s: %s", i, colLabel, diff);
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private String columnLabel(int j) {
            List<String> names = settings.columns.namestrs;
            if (names != null && j < names.size()) return String.format(Locale.ROOT, "column %d (%s)", j, names.get(j));
            return String.format(Locale.ROOT, "column %d", j);
        }

        private String columnNamesPreview(int count) {
            List<String> names = settings.columns.namestrs;
            if (names == null || names.isEmpty() || count <= 0) return "";
            int available = Math.min(count, names.size());
            if (available <= 4) return names.subList(0, available).toString();
            return "[" + names.getFirst() + ".." + names.get(available - 1) + "]";
        }
    }

    protected abstract static class CqlRunOp<R> implements RunOp {

        final ClientWrapper client;
        final String query;
        final Object queryId;
        final List<Object> params;
        final ByteBuffer key;
        final ResultHandler<R> handler;
        R result;

        private CqlRunOp(
                ClientWrapper client,
                String query,
                Object queryId,
                ResultHandler<R> handler,
                List<Object> params,
                ByteBuffer key) {
            this.client = client;
            this.query = query;
            this.queryId = queryId;
            this.handler = handler;
            this.params = params;
            this.key = key;
        }

        @Override
        public boolean run() throws Exception {
            result = queryId != null
                    ? client.execute(queryId, key, params, handler)
                    : client.execute(query, key, params, handler);
            return validate(result);
        }

        public abstract boolean validate(R result);
    }

    @Override
    public void run(StressClient client) throws IOException {
        run(new ClientWrapper(client, settings));
    }

    protected static final class ClientWrapper {
        private final StressClient client;
        private final StressSettings settings;

        private ClientWrapper(StressClient client, StressSettings settings) {
            this.client = client;
            this.settings = settings;
        }

        <R> R execute(String query, ByteBuffer key, List<Object> queryParams, ResultHandler<R> handler) {
            return handler.apply(client.execute(
                    formatCqlQuery(query, queryParams),
                    settings.command.consistencyLevel,
                    settings.command.serialConsistencyLevel));
        }

        <R> R execute(Object statement, ByteBuffer key, List<Object> queryParams, ResultHandler<R> handler) {
            return handler.apply(client.execute(
                    ((StressPreparedStatement) statement).bind(queryParams.toArray()),
                    settings.command.consistencyLevel,
                    settings.command.serialConsistencyLevel));
        }

        Object createPreparedStatement(String cqlQuery) {
            return client.prepare(cqlQuery);
        }
    }

    @FunctionalInterface
    protected interface ResultHandler<V> extends Function<StressResult, V> {}

    protected static final class RowCountHandler implements ResultHandler<Integer> {
        static final RowCountHandler INSTANCE = new RowCountHandler();

        @Override
        public Integer apply(StressResult rows) {
            return rows == null ? 0 : rows.rows().size();
        }
    }

    protected static final class RowsHandler implements ResultHandler<ByteBuffer[][]> {
        static final RowsHandler INSTANCE = new RowsHandler();

        @Override
        public ByteBuffer[][] apply(StressResult result) {
            if (result == null) return EMPTY_BYTE_BUFFERS;
            return result.rows().toArray(ByteBuffer[][]::new);
        }
    }

    private static String getUnQuotedCqlBlob(ByteBuffer term) {
        return "0x" + ByteBufferUtil.bytesToHex(term);
    }

    private static String formatCqlQuery(String query, List<Object> parms) {
        int marker;
        int position = 0;
        StringBuilder result = new StringBuilder();

        marker = query.indexOf('?');
        if (marker == -1 || parms.isEmpty()) return query;

        for (Object parm : parms) {
            result.append(query.substring(position, marker));

            if (parm instanceof ByteBuffer buffer) result.append(getUnQuotedCqlBlob(buffer));
            else if (parm instanceof Long) result.append(parm);
            else throw new AssertionError();

            position = marker + 1;
            marker = query.indexOf('?', position + 1);
            if (marker == -1) break;
        }

        if (position < query.length()) result.append(query.substring(position));

        return result.toString();
    }

    protected String wrapInQuotes(String string) {
        return "\"" + string + "\"";
    }

    private static long totalBytes(List<ByteBuffer> bufs) {
        long total = 0;
        for (ByteBuffer bb : bufs) if (bb != null) total += bb.remaining();
        return total;
    }

    private static long totalBytes(ByteBuffer[] bufs) {
        long total = 0;
        for (ByteBuffer bb : bufs) if (bb != null) total += bb.remaining();
        return total;
    }
}
