// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

public record ColumnSchema(String name, CqlType type, boolean descending) {
    public ColumnSchema(String name, CqlType type) {
        this(name, type, false);
    }
}
