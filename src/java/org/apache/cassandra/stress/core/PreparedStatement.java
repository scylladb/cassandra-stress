// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.core;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import java.util.List;
import java.util.stream.StreamSupport;
import org.apache.cassandra.stress.util.ConsistencyLevel;

public class PreparedStatement {
    private final com.datastax.oss.driver.api.core.cql.PreparedStatement statement;
    private volatile ConsistencyLevel consistencyLevel;
    private volatile ConsistencyLevel serialConsistencyLevel;

    public PreparedStatement(com.datastax.oss.driver.api.core.cql.PreparedStatement statement) {
        this.statement = statement;
    }

    public ColumnDefinitions getVariables() {
        return statement.getVariableDefinitions();
    }

    public List<String> getColumnNames() {
        return StreamSupport.stream(statement.getVariableDefinitions().spliterator(), false)
                .map(ColumnDefinition::getName)
                .map(CqlIdentifier::asInternal)
                .toList();
    }

    public ConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }

    public void setConsistencyLevel(ConsistencyLevel level) {
        this.consistencyLevel = level;
    }

    public ConsistencyLevel getSerialConsistencyLevel() {
        return serialConsistencyLevel;
    }

    public void setSerialConsistencyLevel(ConsistencyLevel level) {
        this.serialConsistencyLevel = level;
    }

    public String getQueryString() {
        return statement.getQuery();
    }

    public BoundStatement bind(Object... values) {
        BoundStatementBuilder builder = statement.boundStatementBuilder(values);
        if (consistencyLevel != null) builder.setConsistencyLevel(consistencyLevel.toDriver());
        if (serialConsistencyLevel != null) builder.setSerialConsistencyLevel(serialConsistencyLevel.toDriver());
        return builder.build();
    }
}
