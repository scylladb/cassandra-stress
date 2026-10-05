package org.apache.cassandra.stress.core;

public class ColumnMetadata {
    final Object metadata;

    public ColumnMetadata(com.datastax.driver.core.ColumnMetadata metadata) {
        this.metadata = metadata;
    }

    public ColumnMetadata(com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata metadata) {
        this.metadata = metadata;
    }

    public com.datastax.driver.core.ColumnMetadata toV3Value() {
        return (com.datastax.driver.core.ColumnMetadata) metadata;
    }

    public com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata toV4Value() {
        return (com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata) metadata;
    }

    public String getName() {
        if (metadata instanceof com.datastax.driver.core.ColumnMetadata) {
            return toV3Value().getName();
        }
        return toV4Value().getName().toString();
    }

    public DataType getType() {
        if (metadata instanceof com.datastax.driver.core.ColumnMetadata) {
            return new DataType(toV3Value().getType());
        }
        return new DataType(toV4Value().getType());
    }

    @Override
    public int hashCode() {
        return metadata.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (!(other instanceof org.apache.cassandra.stress.core.ColumnMetadata casted)) {
            return false;
        } else {

            if (casted.metadata instanceof com.datastax.driver.core.ColumnMetadata) {
                return toV3Value().equals(casted.metadata);
            }
            return toV4Value().equals(casted.toV4Value());
        }
    }
}
