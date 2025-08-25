package org.apache.cassandra.stress.util;

import org.apache.cassandra.stress.core.TableMetadata;

public interface MetadataProvider {
  TableMetadata getTableMetadata(String keyspace, String tableName);
}
