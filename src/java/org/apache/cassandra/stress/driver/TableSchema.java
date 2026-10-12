// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record TableSchema(
        String keyspace,
        String name,
        List<ColumnSchema> partitionKey,
        List<ColumnSchema> clusteringColumns,
        List<ColumnSchema> valueColumns) {
    public TableSchema {
        partitionKey = List.copyOf(partitionKey);
        clusteringColumns = List.copyOf(clusteringColumns);
        valueColumns = valueColumns.stream()
                .sorted(Comparator.comparing(ColumnSchema::name))
                .toList();
    }

    public List<ColumnSchema> primaryKey() {
        List<ColumnSchema> key = new ArrayList<>(partitionKey);
        key.addAll(clusteringColumns);
        return List.copyOf(key);
    }

    public List<ColumnSchema> columns() {
        List<ColumnSchema> columns = new ArrayList<>(primaryKey());
        columns.addAll(valueColumns);
        return List.copyOf(columns);
    }
}
