// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.util.List;
import org.apache.cassandra.stress.util.ConsistencyLevel;

public abstract class AbstractPreparedStatement implements StressPreparedStatement {
    private final String query;
    private final List<String> columnNames;
    private volatile ConsistencyLevel consistencyLevel;
    private volatile ConsistencyLevel serialConsistencyLevel;

    protected AbstractPreparedStatement(String query, List<String> columnNames) {
        this.query = query;
        this.columnNames = List.copyOf(columnNames);
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

    protected ConsistencyLevel consistencyOr(ConsistencyLevel fallback) {
        return consistencyLevel != null ? consistencyLevel : fallback;
    }

    protected ConsistencyLevel serialConsistencyOr(ConsistencyLevel fallback) {
        return serialConsistencyLevel != null ? serialConsistencyLevel : fallback;
    }
}
