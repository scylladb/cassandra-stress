// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.core;

import com.datastax.oss.driver.api.core.type.CustomType;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.ListType;
import com.datastax.oss.driver.api.core.type.MapType;
import com.datastax.oss.driver.api.core.type.SetType;
import com.datastax.oss.driver.api.core.type.TupleType;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import java.util.Locale;
import java.util.Set;

public final class CqlTypes {
    private static final Set<String> GENERATED_TYPES = Set.of(
            "ASCII",
            "TEXT",
            "VARCHAR",
            "BIGINT",
            "COUNTER",
            "BLOB",
            "BOOLEAN",
            "DECIMAL",
            "DOUBLE",
            "FLOAT",
            "INET",
            "INT",
            "VARINT",
            "TIMESTAMP",
            "UUID",
            "TIMEUUID",
            "TINYINT",
            "SMALLINT",
            "TIME",
            "DATE");

    private CqlTypes() {}

    public static String name(DataType type) {
        if (type instanceof ListType) return "LIST";
        if (type instanceof SetType) return "SET";
        if (type instanceof MapType) return "MAP";
        if (type instanceof UserDefinedType) return "UDT";
        if (type instanceof TupleType) return "TUPLE";
        if (type instanceof CustomType) return "CUSTOM";
        return type.asCql(false, true).toUpperCase(Locale.ROOT);
    }

    public static String elementName(DataType type) {
        return switch (type) {
            case ListType list -> name(list.getElementType());
            case SetType set -> name(set.getElementType());
            default -> "";
        };
    }

    public static boolean isFrozen(DataType type) {
        return switch (type) {
            case ListType list -> list.isFrozen();
            case SetType set -> set.isFrozen();
            case MapType map -> map.isFrozen();
            case UserDefinedType udt -> udt.isFrozen();
            default -> false;
        };
    }

    public static boolean isSupported(DataType type) {
        return switch (type) {
            case ListType list -> GENERATED_TYPES.contains(name(list.getElementType()));
            case SetType set -> GENERATED_TYPES.contains(name(set.getElementType()));
            default -> GENERATED_TYPES.contains(name(type));
        };
    }
}
