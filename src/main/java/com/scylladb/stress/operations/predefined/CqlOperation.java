/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.scylladb.stress.operations.predefined;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Function;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.scylladb.stress.core.PreparedStatement;
import com.scylladb.stress.generate.PartitionGenerator;
import com.scylladb.stress.generate.SeedManager;
import com.scylladb.stress.report.Timer;
import com.scylladb.stress.settings.Command;
import com.scylladb.stress.settings.ConnectionStyle;
import com.scylladb.stress.settings.StressSettings;
import com.scylladb.stress.util.JavaDriverClient;
import com.scylladb.stress.util.JavaDriverV4Client;
import com.scylladb.utils.ByteBufferUtil;

public abstract class CqlOperation<V> extends PredefinedOperation {

    public static final ByteBuffer[][] EMPTY_BYTE_BUFFERS = new ByteBuffer[0][];
    public static final byte[][] EMPTY_BYTE_ARRAYS = new byte[0][];

    protected abstract List<Object> getQueryParameters(byte[] key);

    protected abstract String buildQuery();

    protected abstract CqlRunOp<V> buildRunOp(ClientWrapper client, String query, Object queryId, List<Object> params, ByteBuffer key);

    public CqlOperation(Command type, Timer timer, PartitionGenerator generator, SeedManager seedManager, StressSettings settings) {
        super(type, timer, generator, seedManager, settings);
        if (settings.columns().variableColumnCount)
            throw new IllegalStateException("Variable column counts are not implemented for CQL");
    }

    protected CqlRunOp<V> run(final ClientWrapper client, final List<Object> queryParams, final ByteBuffer key) throws IOException {
        final CqlRunOp<V> op;
        if (settings.mode().style == ConnectionStyle.CQL_PREPARED) {
            final Object id;
            Object idobj = getCqlCache();
            if (idobj == null) {
                id = client.createPreparedStatement(buildQuery());

                storeCqlCache(id);
            } else
                id = idobj;

            op = buildRunOp(client, null, id, queryParams, key);
        } else {
            final String query;
            Object qobj = getCqlCache();
            if (qobj == null)
                storeCqlCache(query = buildQuery());
            else
                query = qobj.toString();

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

    // Classes to process Cql results

    // Always succeeds so long as the query executes without error; provides a keyCount to increment on instantiation
    protected final class CqlRunOpAlwaysSucceed extends CqlRunOp<Integer> {

        final int keyCount;

        protected CqlRunOpAlwaysSucceed(ClientWrapper client, String query, Object queryId, List<Object> params, ByteBuffer key, int keyCount) {
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

    // Succeeds so long as the result set is nonempty, and the query executes without error
    protected final class CqlRunOpTestNonEmpty extends CqlRunOp<Integer> {

        protected CqlRunOpTestNonEmpty(ClientWrapper client, String query, Object queryId, List<Object> params, ByteBuffer key) {
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

        // a null value for an item in expect means we just check the row is present
        protected CqlRunOpMatchResults(ClientWrapper client, String query, Object queryId, List<Object> params, ByteBuffer key, List<List<ByteBuffer>> expect) {
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

        public boolean validate(ByteBuffer[][] result) {
            if (!settings.errors().skipReadValidation) {
                if (result.length != expect.size())
                    return false;
                for (int i = 0; i < result.length; i++)
                    if (expect.get(i) != null && !expect.get(i).equals(Arrays.asList(result[i])))
                        return false;
            }
            return true;
        }
    }

    // Cql
    protected abstract static class CqlRunOp<V> implements RunOp {

        final ClientWrapper client;
        final String query;
        final Object queryId;
        final List<Object> params;
        final ByteBuffer key;
        final ResultHandler<V> handler;
        V result;

        private CqlRunOp(ClientWrapper client, String query, Object queryId, ResultHandler<V> handler, List<Object> params, ByteBuffer key) {
            this.client = client;
            this.query = query;
            this.queryId = queryId;
            this.handler = handler;
            this.params = params;
            this.key = key;
        }

        @Override
        public boolean run() throws Exception {
            return queryId != null
                    ? validate(result = client.execute(queryId, key, params, handler))
                    : validate(result = client.execute(query, key, params, handler));
        }

        public abstract boolean validate(V result);

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
        public <V> V execute(String query, ByteBuffer key, List<Object> queryParams, ResultHandler<V> handler) {
            String formattedQuery = formatCqlQuery(query, queryParams);
            return handler.javaDriverHandler().apply(client.execute(formattedQuery, settings.command().consistencyLevel, settings.command().serialConsistencyLevel));
        }

        @Override
        public <V> V execute(Object stmt, ByteBuffer key, List<Object> queryParams, ResultHandler<V> handler) {
            return handler.javaDriverHandler().apply(
                    client.executePrepared(
                            (PreparedStatement) stmt,
                            queryParams,
                            settings.command().consistencyLevel,
                            settings.command().serialConsistencyLevel));
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
        public <V> V execute(String query, ByteBuffer key, List<Object> queryParams, ResultHandler<V> handler) {
            String formattedQuery = formatCqlQuery(query, queryParams);
            return handler.javaDriverV4Handler().apply(client.execute(formattedQuery, settings.command().consistencyLevel, settings.command().serialConsistencyLevel));
        }

        @Override
        public <V> V execute(Object stmt, ByteBuffer key, List<Object> queryParams, ResultHandler<V> handler) {
            return handler.javaDriverV4Handler().apply(
                    client.executePrepared(
                            (PreparedStatement) stmt,
                            queryParams,
                            settings.command().consistencyLevel,
                            settings.command().serialConsistencyLevel));
        }

        @Override
        public Object createPreparedStatement(String cqlQuery) {
            return client.prepare(cqlQuery);
        }
    }

    // interface for building functions to standardise results from each client
    protected interface ResultHandler<V> {
        Function<com.datastax.oss.driver.api.core.cql.ResultSet, V> javaDriverV4Handler();

        Function<ResultSet, V> javaDriverHandler();
    }

    protected static class RowCountHandler implements ResultHandler<Integer> {
        static final RowCountHandler INSTANCE = new RowCountHandler();

        @Override
        public Function<com.datastax.oss.driver.api.core.cql.ResultSet, Integer> javaDriverV4Handler() {
            return rows -> {
                if (rows == null)
                    return 0;
                return rows.all().size();
            };
        }

        @Override
        public Function<ResultSet, Integer> javaDriverHandler() {
            return rows -> {
                if (rows == null)
                    return 0;
                return rows.all().size();
            };
        }
    }

    // Processes results from each client into an array of all key bytes returned
    protected static final class RowsHandler implements ResultHandler<ByteBuffer[][]> {
        static final RowsHandler INSTANCE = new RowsHandler();

        @Override
        public Function<com.datastax.oss.driver.api.core.cql.ResultSet, ByteBuffer[][]> javaDriverV4Handler() {
            {
                return result -> {
                    if (result == null)
                        return EMPTY_BYTE_BUFFERS;
                    List<com.datastax.oss.driver.api.core.cql.Row> rows = result.all();

                    ByteBuffer[][] r = new ByteBuffer[rows.size()][];
                    for (int i = 0; i < r.length; i++) {
                        com.datastax.oss.driver.api.core.cql.Row row = rows.get(i);
                        r[i] = new ByteBuffer[row.getColumnDefinitions().size()];
                        for (int j = 0; j < row.getColumnDefinitions().size(); j++)
                            r[i][j] = row.getByteBuffer(j);
                    }
                    return r;
                };
            }
        }


        @Override
        public Function<ResultSet, ByteBuffer[][]> javaDriverHandler() {
            return result -> {
                if (result == null)
                    return EMPTY_BYTE_BUFFERS;
                List<Row> rows = result.all();

                ByteBuffer[][] r = new ByteBuffer[rows.size()][];
                for (int i = 0; i < r.length; i++) {
                    Row row = rows.get(i);
                    r[i] = new ByteBuffer[row.getColumnDefinitions().size()];
                    for (int j = 0; j < row.getColumnDefinitions().size(); j++)
                        r[i][j] = row.getBytes(j);
                }
                return r;
            };
        }
    }

    // Processes results from each client into an array of all key bytes returned
    protected static final class KeysHandler implements ResultHandler<byte[][]> {
        static final KeysHandler INSTANCE = new KeysHandler();

        @Override
        public Function<com.datastax.oss.driver.api.core.cql.ResultSet, byte[][]> javaDriverV4Handler() {
            return result -> {
                if (result == null)
                    return EMPTY_BYTE_ARRAYS;
                List<com.datastax.oss.driver.api.core.cql.Row> rows = result.all();
                byte[][] r = new byte[rows.size()][];
                for (int i = 0; i < r.length; i++)
                    r[i] = rows.get(i).getByteBuffer(0).array();
                return r;
            };
        }

        @Override
        public Function<ResultSet, byte[][]> javaDriverHandler() {
            return result -> {
                if (result == null)
                    return EMPTY_BYTE_ARRAYS;
                List<Row> rows = result.all();
                byte[][] r = new byte[rows.size()][];
                for (int i = 0; i < r.length; i++)
                    r[i] = rows.get(i).getBytes(0).array();
                return r;
            };
        }
    }

    private static String getUnQuotedCqlBlob(ByteBuffer term) {
        return "0x" + ByteBufferUtil.bytesToHex(term);
    }

    /**
     * Constructs a CQL query string by replacing instances of the character
     * '?', with the corresponding parameter.
     *
     * @param query base query string to format
     * @param parms sequence of string query parameters
     * @return formatted CQL query string
     */
    private static String formatCqlQuery(String query, List<Object> parms) {
        int marker, position = 0;
        StringBuilder result = new StringBuilder();

        if (-1 == (marker = query.indexOf('?')) || parms.isEmpty())
            return query;

        for (Object parm : parms) {
            result.append(query, position, marker);

            if (parm instanceof ByteBuffer) {
                result.append(getUnQuotedCqlBlob((ByteBuffer) parm));
            } else if (parm instanceof Long) {
                result.append(parm);
            } else {
                throw new AssertionError();
            }

            position = marker + 1;
            if (-1 == (marker = query.indexOf('?', position + 1))) {
                break;
            }
        }

        if (position < query.length()) {
            result.append(query.substring(position));
        }

        return result.toString();
    }

    protected String wrapInQuotes(String string) {
        return "\"" + string + "\"";
    }
}
