// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.util.List;
import org.apache.cassandra.stress.util.ConsistencyLevel;

public abstract class AbstractPreparedStatement implements StressPreparedStatement {
    private final String query;
    private final List<String> columnNames;
    private final boolean idempotent;
    private volatile ConsistencyLevel consistencyLevel;
    private volatile ConsistencyLevel serialConsistencyLevel;

    protected AbstractPreparedStatement(String query, List<String> columnNames) {
        this(query, columnNames, false);
    }

    protected AbstractPreparedStatement(String query, List<String> columnNames, boolean bindsList) {
        this.query = query;
        this.columnNames = List.copyOf(columnNames);
        this.idempotent = !bindsList && StatementIdempotence.isIdempotent(query);
    }

    @Override
    public String getQueryString() {
        return query;
    }

    @Override
    public List<String> getColumnNames() {
        return columnNames;
    }

    @Override
    public boolean isIdempotent() {
        return idempotent;
    }

    @Override
    public ConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }

    @Override
    public void setConsistencyLevel(ConsistencyLevel level) {
        this.consistencyLevel = level;
    }

    @Override
    public ConsistencyLevel getSerialConsistencyLevel() {
        return serialConsistencyLevel;
    }

    @Override
    public void setSerialConsistencyLevel(ConsistencyLevel level) {
        this.serialConsistencyLevel = level;
    }
}
