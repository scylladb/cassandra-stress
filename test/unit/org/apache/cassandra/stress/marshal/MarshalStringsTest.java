package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarshalStringsTest
{
    @ParameterizedTest
    @CsvSource({
        "0, 00:00:00.000000000",
        "1, 00:00:00.000000001",
        "45296789000000, 12:34:56.789000000",
        "86399999999999, 23:59:59.999999999",
    })
    void timePrintsHoursToNanoseconds(long nanos, String expected)
    {
        assertEquals(expected, TimeSerializer.instance.toString(nanos));
    }

    @Test
    void timeValidatesItsLength()
    {
        assertDoesNotThrow(() -> TimeSerializer.instance.validate(ByteBuffer.allocate(8)));
        assertThrows(MarshalException.class, () -> TimeSerializer.instance.validate(ByteBuffer.allocate(4)));
    }

    @Test
    void utf8AcceptsValidText()
    {
        assertDoesNotThrow(() -> UTF8Serializer.instance.validate(ByteBuffer.wrap("zażółć ✓ 😀".getBytes(StandardCharsets.UTF_8))));
    }

    @ParameterizedTest
    @CsvSource({ "c3", "e282", "f09f98", "80", "c0af", "f8888080" })
    void utf8RejectsInvalidSequences(String hex)
    {
        ByteBuffer bytes = ByteBuffer.wrap(java.util.HexFormat.of().parseHex(hex.replace(" ", "")));
        assertThrows(MarshalException.class, () -> UTF8Serializer.instance.validate(bytes));
    }

    @Test
    void cqlLiteralsQuoteText()
    {
        assertEquals("'it''s'", UTF8Type.instance.getSerializer().toCQLLiteral(UTF8Type.instance.decompose("it's")));
        assertEquals("null", UTF8Type.instance.getSerializer().toCQLLiteral(null));
    }
}
