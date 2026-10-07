// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

public class Row {
    private static final Object[] EMPTY_ROW_DATA = new Object[0];

    public final Object[] partitionKey;
    public final Object[] row;

    public Row(Object[] partitionKey) {
        this.partitionKey = partitionKey;
        this.row = EMPTY_ROW_DATA;
    }

    public Row(Object[] partitionKey, Object[] row) {
        this.partitionKey = partitionKey;
        this.row = row;
    }

    public Object get(int column) {
        if (column < 0) {
            return partitionKey[-1 - column];
        }
        return row[column];
    }

    public Row copy() {
        return new Row(partitionKey.clone(), row.clone());
    }
}
