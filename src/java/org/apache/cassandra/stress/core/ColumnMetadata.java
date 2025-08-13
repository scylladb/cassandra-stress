package org.apache.cassandra.stress.core;

public class ColumnMetadata {
  final Object metadata;

  public ColumnMetadata(com.datastax.driver.core.ColumnMetadata metadata) {
    this.metadata = metadata;
  }

  public ColumnMetadata(shaded.com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata metadata) {
    this.metadata = metadata;
  }

  public com.datastax.driver.core.ColumnMetadata  ToV3Value() {
    return (com.datastax.driver.core.ColumnMetadata) metadata;
  }

  public shaded.com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata  ToV4Value() {
    return (shaded.com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata) metadata;
  }

  public String getName() {
    if (metadata instanceof com.datastax.driver.core.ColumnMetadata) {
      return ToV3Value().getName();
    }
    return ToV4Value().getName().toString();
  }

  public DataType getType() {
    if (metadata instanceof com.datastax.driver.core.ColumnMetadata) {
      return new DataType(ToV3Value().getType());
    }
    return new DataType(ToV4Value().getType());
  }
}
