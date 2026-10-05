package org.apache.cassandra.stress.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.internal.core.type.UserDefinedTypeBuilder;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CqlTypesTest {
    private static final UserDefinedType ADDRESS = new UserDefinedTypeBuilder(
                    CqlIdentifier.fromInternal("ks"), CqlIdentifier.fromInternal("address"))
            .withField(CqlIdentifier.fromInternal("street"), DataTypes.TEXT)
            .build();

    static Stream<Arguments> types() {
        return Stream.of(
                Arguments.of(DataTypes.TEXT, "TEXT", "", true),
                Arguments.of(DataTypes.ASCII, "ASCII", "", true),
                Arguments.of(DataTypes.BIGINT, "BIGINT", "", true),
                Arguments.of(DataTypes.COUNTER, "COUNTER", "", true),
                Arguments.of(DataTypes.TIMESTAMP, "TIMESTAMP", "", true),
                Arguments.of(DataTypes.DATE, "DATE", "", true),
                Arguments.of(DataTypes.TIME, "TIME", "", true),
                Arguments.of(DataTypes.DURATION, "DURATION", "", false),
                Arguments.of(DataTypes.setOf(DataTypes.TEXT), "SET", "TEXT", true),
                Arguments.of(DataTypes.listOf(DataTypes.INT), "LIST", "INT", true),
                Arguments.of(DataTypes.setOf(DataTypes.listOf(DataTypes.INT)), "SET", "LIST", false),
                Arguments.of(DataTypes.mapOf(DataTypes.TEXT, DataTypes.INT), "MAP", "", false),
                Arguments.of(DataTypes.tupleOf(DataTypes.INT), "TUPLE", "", false),
                Arguments.of(ADDRESS, "UDT", "", false),
                Arguments.of(DataTypes.custom("org.example.Type"), "CUSTOM", "", false));
    }

    @ParameterizedTest
    @MethodSource("types")
    void namesEachTypeTheWayTheGeneratorsDo(DataType type, String name, String element, boolean supported) {
        assertEquals(name, CqlTypes.name(type));
        assertEquals(element, CqlTypes.elementName(type));
        assertEquals(supported, CqlTypes.isSupported(type));
    }

    @Test
    void readsTheFrozenFlag() {
        assertTrue(CqlTypes.isFrozen(DataTypes.frozenSetOf(DataTypes.TEXT)));
        assertTrue(CqlTypes.isFrozen(DataTypes.frozenListOf(DataTypes.TEXT)));
        assertTrue(CqlTypes.isFrozen(DataTypes.frozenMapOf(DataTypes.TEXT, DataTypes.TEXT)));
        assertTrue(CqlTypes.isFrozen(ADDRESS.copy(true)));
        assertFalse(CqlTypes.isFrozen(DataTypes.setOf(DataTypes.TEXT)));
        assertFalse(CqlTypes.isFrozen(DataTypes.TEXT));
    }
}
