package org.apache.cassandra.stress.core;

import org.apache.cassandra.db.ConsistencyLevel;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PreparedStatement {
  final private Object stmt;
  private ConsistencyLevel consistencyLevel;
  private ConsistencyLevel serialConsistencyLevel;

  public PreparedStatement(com.datastax.driver.core.PreparedStatement statement) {
    stmt = statement;
  }

  public PreparedStatement(shaded.com.datastax.oss.driver.api.core.cql.PreparedStatement statement) {
    stmt = statement;
  }

  public com.datastax.driver.core.PreparedStatement ToV3Value() {
    return (com.datastax.driver.core.PreparedStatement) stmt;
  }

  public ColumnDefinitions getVariables() {
    if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
      return new ColumnDefinitions(ToV3Value().getVariables());
    }
    return new ColumnDefinitions(ToV4Value().getVariableDefinitions());
  }

  public List<String> getColumnNames() {
    if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
      return ToV3Value().getVariables().asList().stream().map(
          com.datastax.driver.core.ColumnDefinitions.Definition::getName).collect(Collectors.toList());
    }
    return StreamSupport.stream(ToV4Value().getVariableDefinitions().spliterator(), false).map(d -> d.getName().toString()).collect(Collectors.toList());
  }

  public shaded.com.datastax.oss.driver.api.core.cql.PreparedStatement ToV4Value() {
    return (shaded.com.datastax.oss.driver.api.core.cql.PreparedStatement) stmt;
  }

  public ConsistencyLevel getConsistencyLevel() {
    return consistencyLevel;
  }

  public void setConsistencyLevel(ConsistencyLevel level) {
    this.consistencyLevel = level;
    if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
      this.ToV3Value().setConsistencyLevel(level.ToV3Value());
    }
  }

  public ConsistencyLevel getSerialConsistencyLevel() {
    return serialConsistencyLevel;
  }

  public void setSerialConsistencyLevel(ConsistencyLevel level) {
    if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
      this.ToV3Value().setSerialConsistencyLevel(level.ToV3Value());
    }
    this.serialConsistencyLevel = level;
  }

  public String getQueryString() {
    if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
      return this.ToV3Value().getQueryString();
    }
    return this.ToV4Value().getQuery();
  }

  public BoundStatement bind(Object... vars) {
    if (stmt instanceof com.datastax.driver.core.PreparedStatement) {
      return new BoundStatement(this.ToV3Value().bind(vars));
    }
    shaded.com.datastax.oss.driver.api.core.cql.BoundStatementBuilder stmt = this.ToV4Value().boundStatementBuilder(vars);
    if (consistencyLevel != null) {
      stmt.setConsistencyLevel(consistencyLevel.ToV4Value());
    }
    if (serialConsistencyLevel != null) {
      stmt.setSerialConsistencyLevel(serialConsistencyLevel.ToV4Value());
    }
    return new BoundStatement(stmt.build());
  }
}
