package org.apache.cassandra.stress.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.apache.cassandra.stress.driver.CqlType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CqlTypesTest {
    private static CqlType collection(String name, CqlType element) {
        return new CqlType(name, List.of(element), false);
    }

    static Stream<Arguments> types() {
        return Stream.of(
                Arguments.of(CqlType.of("TEXT"), "TEXT", "", true),
                Arguments.of(CqlType.of("ASCII"), "ASCII", "", true),
                Arguments.of(CqlType.of("BIGINT"), "BIGINT", "", true),
                Arguments.of(CqlType.of("COUNTER"), "COUNTER", "", true),
                Arguments.of(CqlType.of("TIMESTAMP"), "TIMESTAMP", "", true),
                Arguments.of(CqlType.of("DATE"), "DATE", "", true),
                Arguments.of(CqlType.of("TIME"), "TIME", "", true),
                Arguments.of(CqlType.of("DURATION"), "DURATION", "", false),
                Arguments.of(collection("SET", CqlType.of("TEXT")), "SET", "TEXT", true),
                Arguments.of(collection("LIST", CqlType.of("INT")), "LIST", "INT", true),
                Arguments.of(collection("SET", collection("LIST", CqlType.of("INT"))), "SET", "LIST", false),
                Arguments.of(
                        new CqlType("MAP", List.of(CqlType.of("TEXT"), CqlType.of("INT")), false), "MAP", "", false),
                Arguments.of(CqlType.of("TUPLE"), "TUPLE", "", false),
                Arguments.of(CqlType.of("UDT"), "UDT", "", false),
                Arguments.of(CqlType.of("CUSTOM"), "CUSTOM", "", false));
    }

    @ParameterizedTest
    @MethodSource("types")
    void namesEachTypeTheWayTheGeneratorsDo(CqlType type, String name, String element, boolean supported) {
        assertEquals(name, CqlTypes.name(type));
        assertEquals(element, CqlTypes.elementName(type));
        assertEquals(supported, CqlTypes.isSupported(type));
    }

    @Test
    void readsTheFrozenFlag() {
        assertTrue(CqlTypes.isFrozen(new CqlType("SET", List.of(CqlType.of("TEXT")), true)));
        assertFalse(CqlTypes.isFrozen(collection("SET", CqlType.of("TEXT"))));
        assertFalse(CqlTypes.isFrozen(CqlType.of("TEXT")));
    }
}
