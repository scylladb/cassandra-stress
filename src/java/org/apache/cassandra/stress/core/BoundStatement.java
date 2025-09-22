package org.apache.cassandra.stress.core;

public class BoundStatement {
  final private Object stmt;

  public BoundStatement(com.datastax.driver.core.BoundStatement statement) {
    stmt = statement;
  }

  public BoundStatement(shaded.com.datastax.oss.driver.api.core.cql.BoundStatement statement) {
    stmt = statement;
  }

  public com.datastax.driver.core.BoundStatement ToV3Value() {
    return (com.datastax.driver.core.BoundStatement) stmt;
  }

  public shaded.com.datastax.oss.driver.api.core.cql.BoundStatement ToV4Value() {
    return (shaded.com.datastax.oss.driver.api.core.cql.BoundStatement) stmt;
  }
}
