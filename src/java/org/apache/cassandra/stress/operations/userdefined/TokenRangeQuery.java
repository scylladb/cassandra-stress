// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

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
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressPage;
import org.apache.cassandra.stress.driver.TableSchema;
import org.apache.cassandra.stress.driver.TokenSlice;
import org.apache.cassandra.stress.generate.TokenRangeIterator;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.CqlNames;

public class TokenRangeQuery extends Operation {
    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<State> currentState = new ThreadLocal<>();

    private final TableSchema tableMetadata;
    private final TokenRangeIterator tokenRangeIterator;
    private final String columns;
    private final int pageSize;
    private final boolean isWarmup;

    public TokenRangeQuery(
            Timer timer,
            StressSettings settings,
            TableSchema tableMetadata,
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

    private static String sanitizeColumns(String columns, TableSchema tableMetadata) {
        if (!"*".equals(columns)) return columns;

        return tableMetadata.columns().stream()
                .map(column -> CqlNames.quote(column.name()))
                .collect(Collectors.joining(", "));
    }

    private static final class State {
        public final String bounds;
        public final String query;
        public Object pagingState;
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

        final StressClient client;

        private JavaDriverRun(StressClient client) {
            this.client = client;
        }

        private static int tokenColumn(List<String> columnNames) {
            for (int i = 0; i < columnNames.size(); i++)
                if (TOKEN_COLUMN_NAME.matcher(columnNames.get(i)).matches()) return i;
            throw new IllegalStateException("Unable to locate token(...) column in result set. "
                    + "This query must project token(partition_key) without aliasing.");
        }

        @Override
        public boolean run() throws Exception {
            State state = currentState.get();
            if (state == null) {
                TokenSlice range = tokenRangeIterator.next();
                if (range == null) return true;

                state = new State(range.format(), buildQuery(range));
                currentState.set(state);
            }

            StressPage page = client.executePage(state.query, pageSize, state.pagingState);
            state.pagingState = page.pagingState();

            List<ByteBuffer[]> rows = page.result().rows();
            rowCount += rows.size();

            if (!rows.isEmpty()) {
                int token = tokenColumn(page.result().columnNames());
                for (ByteBuffer[] row : rows) if (state.partitions.add(row[token])) partitionCount += 1;
            }

            if (page.fullyFetched() || isWarmup) {
                currentState.set(null);
            }

            return true;
        }
    }

    private String buildQuery(TokenSlice tokenRange) {
        List<String> pkColumns = tableMetadata.partitionKey().stream()
                .map(ColumnSchema::name)
                .map(CqlNames::quote)
                .toList();
        String tokenStatement = String.format("token(%s)", String.join(", ", pkColumns));

        return "SELECT " + tokenStatement + ", " + columns + " FROM "
                + CqlNames.quote(tableMetadata.name())
                + " WHERE " + tokenStatement + " > " + tokenRange.start()
                + " AND " + tokenStatement + " <= " + tokenRange.end();
    }

    @Override
    public void run(StressClient client) throws IOException {
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
