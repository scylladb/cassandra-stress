package com.scylladb.stress.core;

public class BoundStatement {
  final private Object stmt;

  public BoundStatement(com.datastax.driver.core.BoundStatement statement) {
    stmt = statement;
  }

  public BoundStatement(com.datastax.oss.driver.api.core.cql.BoundStatement statement) {
    stmt = statement;
  }

  public com.datastax.driver.core.BoundStatement ToV3Value() {
    return (com.datastax.driver.core.BoundStatement) stmt;
  }

  public com.datastax.oss.driver.api.core.cql.BoundStatement ToV4Value() {
    return (com.datastax.oss.driver.api.core.cql.BoundStatement) stmt;
  }
}
