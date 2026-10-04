package org.apache.cassandra.stress.core;

import java.util.List;

public class TableMetadata {
  final private Object metadata;

  public TableMetadata(com.datastax.driver.core.TableMetadata metadata) {
    this.metadata = metadata;
  }

  public TableMetadata(com.datastax.oss.driver.api.core.metadata.schema.TableMetadata metadata) {
    this.metadata = metadata;
  }

  public com.datastax.driver.core.TableMetadata ToV3Value() {
    return (com.datastax.driver.core.TableMetadata) metadata;
  }

  public com.datastax.oss.driver.api.core.metadata.schema.TableMetadata ToV4Value() {
    return (com.datastax.oss.driver.api.core.metadata.schema.TableMetadata) metadata;
  }

  public String getName() {
    if (metadata instanceof com.datastax.driver.core.TableMetadata) {
      return ToV3Value().getName();
    }
    return ToV4Value().getName().toString();
  }

  public List<String> getColumnNames() {
    if (metadata instanceof com.datastax.driver.core.TableMetadata) {
      return ToV3Value().getColumns().stream().map(com.datastax.driver.core.ColumnMetadata::getName).toList();
    }
    return ToV4Value().getColumns().keySet().stream().map(com.datastax.oss.driver.api.core.CqlIdentifier::toString).toList();
  }

  public List<String> getPrimaryKeyNames() {
    if (metadata instanceof com.datastax.driver.core.TableMetadata) {
      return ToV3Value().getPrimaryKey().stream().map(com.datastax.driver.core.ColumnMetadata::getName).toList();
    }
    return ToV4Value().getPrimaryKey().stream().map(c -> c.getName().toString()).toList();
  }

  public List<String> getPartitionKeyNames() {
    if (metadata instanceof com.datastax.driver.core.TableMetadata) {
      return ToV3Value().getPartitionKey().stream().map(com.datastax.driver.core.ColumnMetadata::getName).toList();
    }
    return ToV4Value().getPartitionKey().stream().map(c -> c.getName().toString()).toList();
  }

  public List<String> getClusteringColumnNames() {
    if (metadata instanceof com.datastax.driver.core.TableMetadata) {
      return ToV3Value().getClusteringColumns().stream().map(com.datastax.driver.core.ColumnMetadata::getName).toList();
    }
    return ToV4Value().getClusteringColumns().keySet().stream().map(c -> c.getName().toString()).toList();
  }

  public List<ColumnMetadata> getPrimaryKey() {
    if (metadata instanceof com.datastax.driver.core.TableMetadata) {
      return ToV3Value().getPrimaryKey().stream().map(ColumnMetadata::new).toList();
    }
    return ToV4Value().getPrimaryKey().stream().map(ColumnMetadata::new).toList();
  }

  public List<ColumnMetadata> getPartitionKey() {
    if (metadata instanceof com.datastax.driver.core.TableMetadata) {
      return ToV3Value().getPartitionKey().stream().map(ColumnMetadata::new).toList();
    }
    return ToV4Value().getPartitionKey().stream().map(ColumnMetadata::new).toList();
  }

  public List<ColumnMetadata> getClusteringColumns() {
    if (metadata instanceof com.datastax.driver.core.TableMetadata) {
      return ToV3Value().getClusteringColumns().stream().map(ColumnMetadata::new).toList();
    }
    return ToV4Value().getClusteringColumns().keySet().stream().map(ColumnMetadata::new).toList();
  }

  public List<ColumnMetadata> getColumns() {
    if (metadata instanceof com.datastax.driver.core.TableMetadata) {
      return ToV3Value().getColumns().stream().map(ColumnMetadata::new).toList();
    }
    return ToV4Value().getColumns().values().stream().map(ColumnMetadata::new).toList();
  }
}
