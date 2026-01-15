package com.scylladb.stress.core;

public enum BatchStatementType {
  LOGGED,
  UNLOGGED,
  COUNTER;

  public com.datastax.driver.core.BatchStatement.Type ToV3Value() {
      return switch (this) {
          case LOGGED -> com.datastax.driver.core.BatchStatement.Type.LOGGED;
          case UNLOGGED -> com.datastax.driver.core.BatchStatement.Type.UNLOGGED;
          case COUNTER -> com.datastax.driver.core.BatchStatement.Type.COUNTER;
          default -> throw new AssertionError();
      };
  }

  public com.datastax.oss.driver.api.core.cql.BatchType ToV4Value() {
      return switch (this) {
          case LOGGED -> com.datastax.oss.driver.api.core.cql.BatchType.LOGGED;
          case UNLOGGED -> com.datastax.oss.driver.api.core.cql.BatchType.UNLOGGED;
          case COUNTER -> com.datastax.oss.driver.api.core.cql.BatchType.COUNTER;
          default -> throw new AssertionError();
      };
  }
}
