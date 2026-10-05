// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder;
import com.datastax.oss.driver.api.core.metadata.TokenMap;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.metadata.token.Token;
import com.datastax.oss.driver.api.core.metadata.token.TokenRange;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.cassandra.stress.Operation;
import org.apache.cassandra.stress.StressYaml;
import org.apache.cassandra.stress.WorkManager;
import org.apache.cassandra.stress.generate.TokenRangeIterator;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.JavaDriverClient;

public class TokenRangeQuery extends Operation {
    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<State> currentState = new ThreadLocal<>();

    private final TableMetadata tableMetadata;
    private final TokenRangeIterator tokenRangeIterator;
    private final String columns;
    private final int pageSize;
    private final boolean isWarmup;

    public TokenRangeQuery(
            Timer timer,
            StressSettings settings,
            TableMetadata tableMetadata,
            TokenRangeIterator tokenRangeIterator,
            StressYaml.TokenRangeQueryDef def,
            boolean isWarmup) {
        super(timer, settings);
        this.tableMetadata = tableMetadata;
        this.tokenRangeIterator = tokenRangeIterator;
        this.columns = sanitizeColumns(def.columns, tableMetadata);
        this.pageSize = isWarmup ? Math.min(100, def.page_size) : def.page_size;
        this.isWarmup = isWarmup;
    }

    private static String sanitizeColumns(String columns, TableMetadata tableMetadata) {
        if (!"*".equals(columns)) return columns;

        return tableMetadata.getColumns().keySet().stream()
                .map(name -> name.asCql(true))
                .collect(Collectors.joining(", "));
    }

    private static final class State {
        public final String bounds;
        public final String query;
        public ByteBuffer pagingState;
        public Set<Object> partitions = new HashSet<>();

        State(String bounds, String query) {
            this.bounds = bounds;
            this.query = query;
        }

        @Override
        public String toString() {
            return bounds;
        }
    }

    abstract static class Runner implements RunOp {
        int partitionCount;
        int rowCount;

        @Override
        public int partitionCount() {
            return partitionCount;
        }

        @Override
        public int rowCount() {
            return rowCount;
        }
    }

    private final class JavaDriverRun extends Runner {
        private static final Pattern TOKEN_COLUMN_NAME = Pattern.compile("(?i)(?:system\\.)?token\\(.*\\)");

        final JavaDriverClient client;

        private JavaDriverRun(JavaDriverClient client) {
            this.client = client;
        }

        private static Token getPartitionKeyToken(Row row) {
            ColumnDefinitions metadata = row.getColumnDefinitions();
            for (int i = 0; i < metadata.size(); i++) {
                String colName = metadata.get(i).getName().asInternal();
                if (TOKEN_COLUMN_NAME.matcher(colName).matches()) return row.getToken(i);
            }
            throw new IllegalStateException("Unable to locate token(...) column in result set. "
                    + "This query must project token(partition_key) without aliasing.");
        }

        @Override
        public boolean run() throws Exception {
            State state = currentState.get();
            if (state == null) {
                TokenRange range = tokenRangeIterator.next();
                if (range == null) return true;

                TokenMap tokenMap = client.getTokenMap()
                        .orElseThrow(() -> new IllegalStateException("The driver has no token map"));
                String bounds = "[" + tokenMap.format(range.getStart()) + ", " + tokenMap.format(range.getEnd()) + "]";
                state = new State(bounds, buildQuery(range, tokenMap));
                currentState.set(state);
            }

            SimpleStatementBuilder statement = new SimpleStatementBuilder(state.query).setPageSize(pageSize);
            if (state.pagingState != null) statement.setPagingState(state.pagingState);

            ResultSet results = client.getSession().execute(statement.build());
            state.pagingState = results.getExecutionInfo().getPagingState();

            int remaining = results.getAvailableWithoutFetching();
            rowCount += remaining;

            for (Row row : results) {
                Object partition = getPartitionKeyToken(row);
                if (!state.partitions.contains(partition)) {
                    partitionCount += 1;
                    state.partitions.add(partition);
                }

                if (--remaining == 0) break;
            }

            if (results.isFullyFetched() || isWarmup) {
                currentState.set(null);
            }

            return true;
        }
    }

    private String buildQuery(TokenRange tokenRange, TokenMap tokenMap) {
        Token start = tokenRange.getStart();
        Token end = tokenRange.getEnd();
        List<String> pkColumns = tableMetadata.getPartitionKey().stream()
                .map(ColumnMetadata::getName)
                .map(name -> name.asCql(true))
                .toList();
        String tokenStatement = String.format("token(%s)", String.join(", ", pkColumns));

        return "SELECT " + tokenStatement + ", " + columns + " FROM "
                + tableMetadata.getName().asCql(true)
                + " WHERE " + tokenStatement + " > " + tokenMap.format(start)
                + " AND " + tokenStatement + " <= " + tokenMap.format(end);
    }

    @Override
    public void run(JavaDriverClient client) throws IOException {
        timeWithRetry(new JavaDriverRun(client));
    }

    @Override
    public int ready(WorkManager workManager) {
        tokenRangeIterator.update();

        if (tokenRangeIterator.exhausted() && currentState.get() == null) return 0;

        int numLeft = workManager.takePermits(1);

        return numLeft > 0 ? 1 : 0;
    }

    @Override
    public String key() {
        State state = currentState.get();
        return state == null ? "-" : state.toString();
    }
}
