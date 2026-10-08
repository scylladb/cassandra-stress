// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.core;

import java.util.Set;
import org.apache.cassandra.stress.driver.CqlType;

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

    public static String name(CqlType type) {
        return type.name();
    }

    public static String elementName(CqlType type) {
        return type.elementName();
    }

    public static boolean isFrozen(CqlType type) {
        return type.frozen();
    }

    public static boolean isSupported(CqlType type) {
        return switch (type.name()) {
            case "LIST", "SET" -> GENERATED_TYPES.contains(type.elementName());
            default -> GENERATED_TYPES.contains(type.name());
        };
    }
}
