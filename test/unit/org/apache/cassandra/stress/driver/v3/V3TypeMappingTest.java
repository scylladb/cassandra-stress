package org.apache.cassandra.stress.driver.v3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.driver.core.DataType;
import java.util.stream.Stream;
import org.apache.cassandra.stress.core.CqlTypes;
import org.apache.cassandra.stress.driver.CqlType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class V3TypeMappingTest {
    static Stream<Arguments> types() {
        return Stream.of(
                Arguments.of(DataType.text(), "TEXT", "", true),
                Arguments.of(DataType.varchar(), "TEXT", "", true),
                Arguments.of(DataType.ascii(), "ASCII", "", true),
                Arguments.of(DataType.bigint(), "BIGINT", "", true),
                Arguments.of(DataType.counter(), "COUNTER", "", true),
                Arguments.of(DataType.timestamp(), "TIMESTAMP", "", true),
                Arguments.of(DataType.date(), "DATE", "", true),
                Arguments.of(DataType.time(), "TIME", "", true),
                Arguments.of(DataType.duration(), "DURATION", "", false),
                Arguments.of(DataType.set(DataType.text()), "SET", "TEXT", true),
                Arguments.of(DataType.list(DataType.cint()), "LIST", "INT", true),
                Arguments.of(DataType.set(DataType.list(DataType.cint())), "SET", "LIST", false),
                Arguments.of(DataType.map(DataType.text(), DataType.cint()), "MAP", "", false),
                Arguments.of(DataType.custom("org.example.Type"), "CUSTOM", "", false));
    }

    @ParameterizedTest
    @MethodSource("types")
    void namesEachTypeTheWayTheGeneratorsDo(DataType type, String name, String element, boolean supported) {
        CqlType mapped = JavaDriverV3Client.type(type);
        assertEquals(name, CqlTypes.name(mapped));
        assertEquals(element, CqlTypes.elementName(mapped));
        assertEquals(supported, CqlTypes.isSupported(mapped));
    }

    @Test
    void readsTheFrozenFlag() {
        assertTrue(JavaDriverV3Client.type(DataType.frozenSet(DataType.text())).frozen());
        assertTrue(JavaDriverV3Client.type(DataType.frozenList(DataType.text())).frozen());
        assertTrue(JavaDriverV3Client.type(DataType.frozenMap(DataType.text(), DataType.text()))
                .frozen());
        assertFalse(JavaDriverV3Client.type(DataType.set(DataType.text())).frozen());
        assertFalse(JavaDriverV3Client.type(DataType.text()).frozen());
    }

    @ParameterizedTest
    @CsvSource({"DESC, true", "desc, true", "ASC, false", "asc, false", "NONE, false"})
    void readsTheClusteringOrderInAnyLetterCase(String clusteringOrder, boolean descending) {
        assertEquals(descending, JavaDriverV3Client.isDescending(clusteringOrder));
    }
}
