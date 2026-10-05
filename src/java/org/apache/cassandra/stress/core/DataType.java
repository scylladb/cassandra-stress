package org.apache.cassandra.stress.core;

import java.util.List;
import java.util.Locale;

public class DataType {
    private final Object type;

    public DataType(com.datastax.driver.core.DataType type) {
        this.type = type;
    }

    public DataType(com.datastax.oss.driver.api.core.type.DataType type) {
        this.type = type;
    }

    public com.datastax.driver.core.DataType toV3Value() {
        return (com.datastax.driver.core.DataType) type;
    }

    public com.datastax.oss.driver.api.core.type.DataType toV4Value() {
        return (com.datastax.oss.driver.api.core.type.DataType) type;
    }

    public String getName() {
        if (type instanceof com.datastax.driver.core.DataType) {
            return toV3Value().getName().name();
        }
        return toV4Value().asCql(false, false).toUpperCase(Locale.ROOT);
    }

    public boolean isFrozen() {
        if (type instanceof com.datastax.driver.core.DataType) {
            return toV3Value().isFrozen();
        }

        if (type instanceof com.datastax.oss.driver.api.core.type.ListType listType) {
            return listType.isFrozen();
        }
        if (type instanceof com.datastax.oss.driver.api.core.type.SetType setType) {
            return setType.isFrozen();
        }
        if (type instanceof com.datastax.oss.driver.api.core.type.MapType mapType) {
            return mapType.isFrozen();
        }
        if (type instanceof com.datastax.oss.driver.api.core.type.UserDefinedType userDefinedType) {
            return userDefinedType.isFrozen();
        }
        return false;
    }

    public String getCollectionElementTypeName() {
        if (type instanceof com.datastax.driver.core.DataType) {
            com.datastax.driver.core.DataType casted = toV3Value();
            if (!casted.isCollection()) {
                return "";
            }
            return toV3Value().getTypeArguments().getFirst().getName().name();
        }

        if (type instanceof com.datastax.oss.driver.api.core.type.ListType listType) {
            return listType.getElementType().asCql(false, false);
        }
        if (type instanceof com.datastax.oss.driver.api.core.type.SetType setType) {
            return setType.getElementType().asCql(false, false);
        }
        if (type instanceof com.datastax.oss.driver.api.core.type.MapType mapType) {
            return mapType.getKeyType().asCql(false, false);
        }
        if (type instanceof com.datastax.oss.driver.api.core.type.UserDefinedType) {
            return "";
        }
        return toV4Value().asCql(false, false);
    }

    public boolean isSupported() {
        if (type instanceof com.datastax.driver.core.DataType) {
            com.datastax.driver.core.DataType dataType = toV3Value();
            if (!dataType.isCollection()) return true;
            List<com.datastax.driver.core.DataType> arguments = dataType.getTypeArguments();
            if (arguments.size() >= 2) return false;
            for (com.datastax.driver.core.DataType argumentType : arguments) {
                if (argumentType.isCollection()) {
                    return false;
                }
            }
            return true;
        }
        return isV4Supported(toV4Value());
    }

    private static boolean isV4Supported(com.datastax.oss.driver.api.core.type.DataType type) {
        if (type instanceof com.datastax.oss.driver.api.core.type.ListType listType) {
            return isV4Supported(listType.getElementType());
        }
        if (type instanceof com.datastax.oss.driver.api.core.type.SetType setType) {
            return isV4Supported(setType.getElementType());
        }
        if (type instanceof com.datastax.oss.driver.api.core.type.MapType mapType) {
            return isV4Supported(mapType.getKeyType()) && isV4Supported(mapType.getValueType());
        }
        if (type instanceof com.datastax.oss.driver.api.core.type.UserDefinedType userDefinedType) {
            return userDefinedType.getFieldTypes().stream().allMatch(DataType::isV4Supported);
        }
        return true;
    }
}
