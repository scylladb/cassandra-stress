// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.datastax.driver.core.PagingState;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import org.apache.cassandra.stress.core.TableMetadata;
import com.datastax.driver.core.Token;
import com.datastax.driver.core.TokenRange;
import org.apache.cassandra.stress.Operation;
import org.apache.cassandra.stress.StressYaml;
import org.apache.cassandra.stress.WorkManager;
import org.apache.cassandra.stress.generate.TokenRangeIterator;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.JavaDriverClient;
import org.apache.cassandra.stress.util.JavaDriverV4Client;

public class TokenRangeQuery extends Operation
{
    private final ThreadLocal<State> currentState = new ThreadLocal<>();

    private final TableMetadata tableMetadata;
    private final TokenRangeIterator tokenRangeIterator;
    private final String columns;
    private final int pageSize;
    private final boolean isWarmup;

    public TokenRangeQuery(Timer timer,
                           StressSettings settings,
                           TableMetadata tableMetadata,
                           TokenRangeIterator tokenRangeIterator,
                           StressYaml.TokenRangeQueryDef def,
                           boolean isWarmup)
    {
        super(timer, settings);
        this.tableMetadata = tableMetadata;
        this.tokenRangeIterator = tokenRangeIterator;
        this.columns = sanitizeColumns(def.columns, tableMetadata);
        this.pageSize = isWarmup ? Math.min(100, def.page_size) : def.page_size;
        this.isWarmup = isWarmup;
    }

    private static String sanitizeColumns(String columns, TableMetadata tableMetadata)
    {
        if (!columns.equals("*"))
            return columns;

        return String.join(", ", tableMetadata.getColumnNames());
    }

    private final static class State
    {
        public final TokenRange tokenRange;
        public final String query;
        public PagingState pagingState;
        public ByteBuffer pagingStateV4;
        public Set<Object> partitions = new HashSet<>();

        public State(TokenRange tokenRange, String query)
        {
            this.tokenRange = tokenRange;
            this.query = query;
        }

        @Override
        public String toString()
        {
            return String.format("[%s, %s]", tokenRange.getStart(), tokenRange.getEnd());
        }
    }

    abstract static class Runner implements RunOp
    {
        int partitionCount;
        int rowCount;

        @Override
        public int partitionCount()
        {
            return partitionCount;
        }

        @Override
        public int rowCount()
        {
            return rowCount;
        }
    }

    private class JavaDriverRun extends Runner
    {
        final JavaDriverClient client;

        private JavaDriverRun(JavaDriverClient client)
        {
            this.client = client;
        }

        public boolean run() throws Exception
        {
            State state = currentState.get();
            if (state == null)
            {
                TokenRange range = tokenRangeIterator.next();
                if (range == null)
                    return true;

                state = new State(range, buildQuery(range));
                currentState.set(state);
            }

            ResultSet results;
            Statement statement = new SimpleStatement(state.query);
            statement.setFetchSize(pageSize);

            if (state.pagingState != null)
                statement.setPagingState(state.pagingState);

            results = client.getSession().execute(statement);
            state.pagingState = results.getExecutionInfo().getPagingState();

            int remaining = results.getAvailableWithoutFetching();
            rowCount += remaining;

            for (Row row : results)
            {
                Object partition = row.getPartitionKeyToken();
                if (!state.partitions.contains(partition))
                {
                    partitionCount += 1;
                    state.partitions.add(partition);
                }

                if (--remaining == 0)
                    break;
            }

            if (results.isExhausted() || isWarmup)
            {
                currentState.set(null);
            }

            return true;
        }
    }

    private class JavaDriverV4Run extends Runner
    {
        final JavaDriverV4Client client;
        private final Pattern TOKEN_COLUMN_NAME = Pattern.compile("(?i)(?:system\\.)?token\\(.*\\)");

        private JavaDriverV4Run(JavaDriverV4Client client)
        {
            this.client = client;
        }

        private com.datastax.oss.driver.api.core.metadata.token.Token getPartitionKeyToken(com.datastax.oss.driver.api.core.cql.Row row)
        {
            com.datastax.oss.driver.api.core.cql.ColumnDefinitions metadata = row.getColumnDefinitions();
            for (int i = 0; i < metadata.size(); i++)
            {
                String colName = metadata.get(i).getName().asInternal();
                if (TOKEN_COLUMN_NAME.matcher(colName).matches())
                    return row.getToken(i);
            }
            throw new IllegalStateException("Unable to locate token(...) column in result set. " +
                                            "This query must project token(partition_key) without aliasing.");
        }

        public boolean run() throws Exception
        {
            State state = currentState.get();
            if (state == null)
            {
                TokenRange range = tokenRangeIterator.next();
                if (range == null)
                    return true;

                state = new State(range, buildQuery(range));
                currentState.set(state);
            }

            com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder statement = new com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder(state.query);
            statement.setFetchSize(pageSize);

            if (state.pagingStateV4 != null)
                statement.setPagingState(state.pagingStateV4);

            com.datastax.oss.driver.api.core.cql.ResultSet results = client.getSession().execute(statement.build());
            state.pagingStateV4 = results.getExecutionInfo().getPagingState();

            int remaining = results.getAvailableWithoutFetching();
            rowCount += remaining;

            for (com.datastax.oss.driver.api.core.cql.Row row : results)
            {
                Object partition = getPartitionKeyToken(row);
                if (!state.partitions.contains(partition))
                {
                    partitionCount += 1;
                    state.partitions.add(partition);
                }

                if (--remaining == 0)
                    break;
            }

            if (results.isFullyFetched() || isWarmup)
            {
                currentState.set(null);
            }

            return true;
        }
    }

    private String buildQuery(TokenRange tokenRange)
    {
        Token start = tokenRange.getStart();
        Token end = tokenRange.getEnd();
        List<String> pkColumns = tableMetadata.getPartitionKeyNames();
        String tokenStatement = String.format("token(%s)", String.join(", ", pkColumns));

        StringBuilder ret = new StringBuilder();
        ret.append("SELECT ");
        ret.append(tokenStatement);
        ret.append(", ");
        ret.append(columns);
        ret.append(" FROM ");
        ret.append(tableMetadata.getName());
        if (start != null || end != null)
            ret.append(" WHERE ");
        if (start != null)
        {
            ret.append(tokenStatement);
            ret.append(" > ");
            ret.append(start.toString());
        }

        if (start != null && end != null)
            ret.append(" AND ");

        if (end != null)
        {
            ret.append(tokenStatement);
            ret.append(" <= ");
            ret.append(end.toString());
        }

        return ret.toString();
    }

    @Override
    public void run(JavaDriverClient client) throws IOException
    {
        timeWithRetry(new JavaDriverRun(client));
    }

    @Override
    public void run(JavaDriverV4Client client) throws IOException
    {
        timeWithRetry(new JavaDriverV4Run(client));
    }

    public int ready(WorkManager workManager)
    {
        tokenRangeIterator.update();

        if (tokenRangeIterator.exhausted() && currentState.get() == null)
            return 0;

        int numLeft = workManager.takePermits(1);

        return numLeft > 0 ? 1 : 0;
    }

    public String key()
    {
        State state = currentState.get();
        return state == null ? "-" : state.toString();
    }
}
