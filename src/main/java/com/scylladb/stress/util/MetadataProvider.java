package com.scylladb.stress.util;

import com.scylladb.stress.core.TableMetadata;

public interface MetadataProvider {
    TableMetadata getTableMetadata(String keyspace, String tableName);
}
