package org.apache.cassandra.stress.core;

import com.datastax.driver.core.utils.MoreObjects;

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

  static Integer hashCode;
  public int hashCode() {
    if  (hashCode != null) {
      return hashCode;
    }
    if  (metadata instanceof com.datastax.driver.core.ColumnMetadata) {
      hashCode = ToV3Value().hashCode();
      return hashCode;
    }
    hashCode = ToV4Value().hashCode();
    return hashCode;
  }

  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof org.apache.cassandra.stress.core.ColumnMetadata)) {
      return false;
    } else {
      org.apache.cassandra.stress.core.ColumnMetadata casted = (org.apache.cassandra.stress.core.ColumnMetadata)other;
      if (casted.metadata instanceof com.datastax.driver.core.ColumnMetadata) {
        return ToV3Value().equals(casted.metadata);
      }
      return ToV4Value().equals(casted.ToV4Value());
    }
  }
}
