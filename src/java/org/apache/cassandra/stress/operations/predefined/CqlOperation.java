// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.predefined;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Function;
import org.apache.cassandra.stress.core.PreparedStatement;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.Command;
import org.apache.cassandra.stress.settings.ConnectionStyle;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ByteBufferUtil;
import org.apache.cassandra.stress.util.JavaDriverClient;
import org.apache.cassandra.stress.util.JavaDriverV4Client;

public abstract class CqlOperation<V> extends PredefinedOperation {

    public static final ByteBuffer[][] EMPTY_BYTE_BUFFERS = new ByteBuffer[0][];
    public static final byte[][] EMPTY_BYTE_ARRAYS = new byte[0][];

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
                                "Data returned was not validated: row count mismatch"
                                        + " (expected %d row(s) / %d bytes total; got %d row(s) / %d bytes total)",
                                expectedRows, expectedBytes, actualRows, actualBytes);
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
                            if (actualSize < 0) diff = String.format("got null (expected %d bytes)", expectedSize);
                            else if (actualSize != expectedSize)
                                diff = String.format("expected %d bytes, got %d bytes", expectedSize, actualSize);
                            else
                                diff = String.format(
                                        "same size (%d bytes) but content differs; expected[0..%d]=%s, got[0..%d]=%s",
                                        expectedSize,
                                        Math.min(15, expectedSize - 1),
                                        hexPreview(expectedVal, 16),
                                        Math.min(15, actualSize - 1),
                                        hexPreview(actualVal, 16));
                            validationError =
                                    String.format("Data returned was not validated: row %d, %s: %s", i, colLabel, diff);
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private String columnLabel(int j) {
            List<String> names = settings.columns.namestrs;
            if (names != null && j < names.size()) return String.format("column %d (%s)", j, names.get(j));
            return String.format("column %d", j);
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
    public void run(JavaDriverClient client) throws IOException {
        run(wrap(client));
    }

    @Override
    public void run(JavaDriverV4Client client) throws IOException {
        run(wrap(client));
    }

    public ClientWrapper wrap(JavaDriverClient client) {
        return new JavaDriverWrapper(client);
    }

    public ClientWrapper wrap(JavaDriverV4Client client) {
        return new JavaDriverV4Wrapper(client);
    }

    protected interface ClientWrapper {
        Object createPreparedStatement(String cqlQuery);

        <V> V execute(Object stmt, ByteBuffer key, List<Object> queryParams, ResultHandler<V> handler);

        <V> V execute(String query, ByteBuffer key, List<Object> queryParams, ResultHandler<V> handler);
    }

    private final class JavaDriverWrapper implements ClientWrapper {
        final JavaDriverClient client;

        private JavaDriverWrapper(JavaDriverClient client) {
            this.client = client;
        }

        @Override
        public <R> R execute(String query, ByteBuffer key, List<Object> queryParams, ResultHandler<R> handler) {
            String formattedQuery = formatCqlQuery(query, queryParams);
            return handler.javaDriverHandler()
                    .apply(client.execute(
                            formattedQuery,
                            settings.command.consistencyLevel,
                            settings.command.serialConsistencyLevel));
        }

        @Override
        public <R> R execute(Object stmt, ByteBuffer key, List<Object> queryParams, ResultHandler<R> handler) {
            return handler.javaDriverHandler()
                    .apply(client.executePrepared(
                            (PreparedStatement) stmt,
                            queryParams,
                            settings.command.consistencyLevel,
                            settings.command.serialConsistencyLevel));
        }

        @Override
        public Object createPreparedStatement(String cqlQuery) {
            return client.prepare(cqlQuery);
        }
    }

    private final class JavaDriverV4Wrapper implements ClientWrapper {
        final JavaDriverV4Client client;

        private JavaDriverV4Wrapper(JavaDriverV4Client client) {
            this.client = client;
        }

        @Override
        public <R> R execute(String query, ByteBuffer key, List<Object> queryParams, ResultHandler<R> handler) {
            String formattedQuery = formatCqlQuery(query, queryParams);
            return handler.javaDriverV4Handler()
                    .apply(client.execute(
                            formattedQuery,
                            settings.command.consistencyLevel,
                            settings.command.serialConsistencyLevel));
        }

        @Override
        public <R> R execute(Object stmt, ByteBuffer key, List<Object> queryParams, ResultHandler<R> handler) {
            return handler.javaDriverV4Handler()
                    .apply(client.executePrepared(
                            (PreparedStatement) stmt,
                            queryParams,
                            settings.command.consistencyLevel,
                            settings.command.serialConsistencyLevel));
        }

        @Override
        public Object createPreparedStatement(String cqlQuery) {
            return client.prepare(cqlQuery);
        }
    }

    protected interface ResultHandler<V> {
        Function<com.datastax.oss.driver.api.core.cql.ResultSet, V> javaDriverV4Handler();

        Function<ResultSet, V> javaDriverHandler();
    }

    protected static class RowCountHandler implements ResultHandler<Integer> {
        static final RowCountHandler INSTANCE = new RowCountHandler();

        @Override
        public Function<com.datastax.oss.driver.api.core.cql.ResultSet, Integer> javaDriverV4Handler() {
            return new Function<com.datastax.oss.driver.api.core.cql.ResultSet, Integer>() {
                @Override
                public Integer apply(com.datastax.oss.driver.api.core.cql.ResultSet rows) {
                    if (rows == null) return 0;
                    return rows.all().size();
                }
            };
        }

        @Override
        public Function<ResultSet, Integer> javaDriverHandler() {
            return new Function<ResultSet, Integer>() {
                @Override
                public Integer apply(ResultSet rows) {
                    if (rows == null) return 0;
                    return rows.all().size();
                }
            };
        }
    }

    protected static final class RowsHandler implements ResultHandler<ByteBuffer[][]> {
        static final RowsHandler INSTANCE = new RowsHandler();

        @Override
        public Function<com.datastax.oss.driver.api.core.cql.ResultSet, ByteBuffer[][]> javaDriverV4Handler() {
            {
                return new Function<com.datastax.oss.driver.api.core.cql.ResultSet, ByteBuffer[][]>() {

                    @Override
                    public ByteBuffer[][] apply(com.datastax.oss.driver.api.core.cql.ResultSet result) {
                        if (result == null) return EMPTY_BYTE_BUFFERS;
                        List<com.datastax.oss.driver.api.core.cql.Row> rows = result.all();

                        ByteBuffer[][] r = new ByteBuffer[rows.size()][];
                        for (int i = 0; i < r.length; i++) {
                            com.datastax.oss.driver.api.core.cql.Row row = rows.get(i);
                            r[i] = new ByteBuffer[row.getColumnDefinitions().size()];
                            for (int j = 0; j < row.getColumnDefinitions().size(); j++) r[i][j] = row.getByteBuffer(j);
                        }
                        return r;
                    }
                };
            }
        }

        @Override
        public Function<ResultSet, ByteBuffer[][]> javaDriverHandler() {
            return new Function<ResultSet, ByteBuffer[][]>() {

                @Override
                public ByteBuffer[][] apply(ResultSet result) {
                    if (result == null) return EMPTY_BYTE_BUFFERS;
                    List<Row> rows = result.all();

                    ByteBuffer[][] r = new ByteBuffer[rows.size()][];
                    for (int i = 0; i < r.length; i++) {
                        Row row = rows.get(i);
                        r[i] = new ByteBuffer[row.getColumnDefinitions().size()];
                        for (int j = 0; j < row.getColumnDefinitions().size(); j++) r[i][j] = row.getBytes(j);
                    }
                    return r;
                }
            };
        }
    }

    protected static final class KeysHandler implements ResultHandler<byte[][]> {
        static final KeysHandler INSTANCE = new KeysHandler();

        @Override
        public Function<com.datastax.oss.driver.api.core.cql.ResultSet, byte[][]> javaDriverV4Handler() {
            return new Function<com.datastax.oss.driver.api.core.cql.ResultSet, byte[][]>() {

                @Override
                public byte[][] apply(com.datastax.oss.driver.api.core.cql.ResultSet result) {

                    if (result == null) return EMPTY_BYTE_ARRAYS;
                    List<com.datastax.oss.driver.api.core.cql.Row> rows = result.all();
                    byte[][] r = new byte[rows.size()][];
                    for (int i = 0; i < r.length; i++)
                        r[i] = rows.get(i).getByteBuffer(0).array();
                    return r;
                }
            };
        }

        @Override
        public Function<ResultSet, byte[][]> javaDriverHandler() {
            return new Function<ResultSet, byte[][]>() {

                @Override
                public byte[][] apply(ResultSet result) {

                    if (result == null) return EMPTY_BYTE_ARRAYS;
                    List<Row> rows = result.all();
                    byte[][] r = new byte[rows.size()][];
                    for (int i = 0; i < r.length; i++)
                        r[i] = rows.get(i).getBytes(0).array();
                    return r;
                }
            };
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
