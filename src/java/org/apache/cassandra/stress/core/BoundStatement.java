package org.apache.cassandra.stress.core;

public class BoundStatement {
    private final Object stmt;

    public BoundStatement(com.datastax.driver.core.BoundStatement statement) {
        stmt = statement;
    }

    public BoundStatement(com.datastax.oss.driver.api.core.cql.BoundStatement statement) {
        stmt = statement;
    }

    public com.datastax.driver.core.BoundStatement toV3Value() {
        return (com.datastax.driver.core.BoundStatement) stmt;
    }

    public com.datastax.oss.driver.api.core.cql.BoundStatement toV4Value() {
        return (com.datastax.oss.driver.api.core.cql.BoundStatement) stmt;
    }
}
