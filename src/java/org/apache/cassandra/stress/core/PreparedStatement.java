package org.apache.cassandra.stress.core;

import java.util.List;
import java.util.stream.StreamSupport;
import org.apache.cassandra.stress.util.ConsistencyLevel;

public class PreparedStatement {
    private final Object stmt;
    private ConsistencyLevel consistencyLevel;
    private ConsistencyLevel serialConsistencyLevel;

    public PreparedStatement(com.datastax.driver.core.PreparedStatement statement) {
        stmt = statement;
    }

    public PreparedStatement(com.datastax.oss.driver.api.core.cql.PreparedStatement statement) {
        stmt = statement;
    }

    public com.datastax.driver.core.PreparedStatement toV3Value() {
        return (com.datastax.driver.core.PreparedStatement) stmt;
    }

    public ColumnDefinitions getVariables() {
        if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
            return new ColumnDefinitions(toV3Value().getVariables());
        }
        return new ColumnDefinitions(toV4Value().getVariableDefinitions());
    }

    public List<String> getColumnNames() {
        if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
            return toV3Value().getVariables().asList().stream()
                    .map(com.datastax.driver.core.ColumnDefinitions.Definition::getName)
                    .toList();
        }
        return StreamSupport.stream(toV4Value().getVariableDefinitions().spliterator(), false)
                .map(d -> d.getName().toString())
                .toList();
    }

    public com.datastax.oss.driver.api.core.cql.PreparedStatement toV4Value() {
        return (com.datastax.oss.driver.api.core.cql.PreparedStatement) stmt;
    }

    public ConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }

    public void setConsistencyLevel(ConsistencyLevel level) {
        this.consistencyLevel = level;
        if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
            this.toV3Value().setConsistencyLevel(level.toV3Value());
        }
    }

    public ConsistencyLevel getSerialConsistencyLevel() {
        return serialConsistencyLevel;
    }

    public void setSerialConsistencyLevel(ConsistencyLevel level) {
        if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
            this.toV3Value().setSerialConsistencyLevel(level.toV3Value());
        }
        this.serialConsistencyLevel = level;
    }

    public String getQueryString() {
        if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
            return this.toV3Value().getQueryString();
        }
        return this.toV4Value().getQuery();
    }

    public BoundStatement bind(Object... vars) {
        if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
            return new BoundStatement(this.toV3Value().bind(vars));
        }
        com.datastax.oss.driver.api.core.cql.BoundStatementBuilder stmt =
                this.toV4Value().boundStatementBuilder(vars);
        if (consistencyLevel != null) {
            stmt.setConsistencyLevel(consistencyLevel.toV4Value());
        }
        if (serialConsistencyLevel != null) {
            stmt.setSerialConsistencyLevel(serialConsistencyLevel.toV4Value());
        }
        return new BoundStatement(stmt.build());
    }
}
