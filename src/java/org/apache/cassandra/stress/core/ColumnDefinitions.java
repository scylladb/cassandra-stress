package org.apache.cassandra.stress.core;

import com.datastax.driver.core.DataType;

public class ColumnDefinitions {
  private final Object columnDefinitions;
  private static final DataType.Name v3DateTypeName = DataType.date().getName();

  public ColumnDefinitions(com.datastax.driver.core.ColumnDefinitions columnDefinitions) {
    this.columnDefinitions = columnDefinitions;
  }

  public ColumnDefinitions(shaded.com.datastax.oss.driver.api.core.cql.ColumnDefinitions columnDefinitions) {
    this.columnDefinitions = columnDefinitions;
  }

  public com.datastax.driver.core.ColumnDefinitions ToV3Value() {
    return (com.datastax.driver.core.ColumnDefinitions) columnDefinitions;
  }

  public shaded.com.datastax.oss.driver.api.core.cql.ColumnDefinitions ToV4Value() {
    return (shaded.com.datastax.oss.driver.api.core.cql.ColumnDefinitions) columnDefinitions;
  }

  public boolean isDateType(int i) {
    if (columnDefinitions instanceof com.datastax.driver.core.ColumnDefinitions) {
      return ToV3Value().getType(i).getName().equals(v3DateTypeName);
    }
    return ToV4Value().get(i).getType() == shaded.com.datastax.oss.driver.api.core.type.DataTypes.DATE;
  }

  public int size() {
    if (columnDefinitions instanceof com.datastax.driver.core.ColumnDefinitions) {
      return ToV3Value().size();
    }
    return ToV4Value().size();
  }
}
