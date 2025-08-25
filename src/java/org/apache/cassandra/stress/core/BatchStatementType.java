package org.apache.cassandra.stress.core;

public enum BatchStatementType {
  LOGGED,
  UNLOGGED,
  COUNTER;

  public com.datastax.driver.core.BatchStatement.Type ToV3Value() {
    switch (this) {
      case LOGGED:
        return com.datastax.driver.core.BatchStatement.Type.LOGGED;
      case UNLOGGED:
        return com.datastax.driver.core.BatchStatement.Type.UNLOGGED;
      case COUNTER:
        return com.datastax.driver.core.BatchStatement.Type.COUNTER;
      default:
        throw new AssertionError();
    }
  }

  public shaded.com.datastax.oss.driver.api.core.cql.BatchType ToV4Value() {
    switch (this) {
      case LOGGED:
        return shaded.com.datastax.oss.driver.api.core.cql.BatchType.LOGGED;
      case UNLOGGED:
        return shaded.com.datastax.oss.driver.api.core.cql.BatchType.UNLOGGED;
      case COUNTER:
        return shaded.com.datastax.oss.driver.api.core.cql.BatchType.COUNTER;
      default:
        throw new AssertionError();
    }
  }
}
