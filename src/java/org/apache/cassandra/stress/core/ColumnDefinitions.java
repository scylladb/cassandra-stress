package org.apache.cassandra.stress.core;

import com.datastax.driver.core.DataType;

public class ColumnDefinitions {
    private final Object columnDefinitions;
    private static final DataType.Name V3_DATE_TYPE_NAME = DataType.date().getName();

    public ColumnDefinitions(com.datastax.driver.core.ColumnDefinitions columnDefinitions) {
        this.columnDefinitions = columnDefinitions;
    }

    public ColumnDefinitions(com.datastax.oss.driver.api.core.cql.ColumnDefinitions columnDefinitions) {
        this.columnDefinitions = columnDefinitions;
    }

    public com.datastax.driver.core.ColumnDefinitions toV3Value() {
        return (com.datastax.driver.core.ColumnDefinitions) columnDefinitions;
    }

    public com.datastax.oss.driver.api.core.cql.ColumnDefinitions toV4Value() {
        return (com.datastax.oss.driver.api.core.cql.ColumnDefinitions) columnDefinitions;
    }

    public boolean isDateType(int i) {
        if (columnDefinitions instanceof com.datastax.driver.core.ColumnDefinitions) {
            return toV3Value().getType(i).getName() == V3_DATE_TYPE_NAME;
        }
        return toV4Value().get(i).getType().equals(com.datastax.oss.driver.api.core.type.DataTypes.DATE);
    }

    public int size() {
        if (columnDefinitions instanceof com.datastax.driver.core.ColumnDefinitions) {
            return toV3Value().size();
        }
        return toV4Value().size();
    }
}
