package org.apache.cassandra.stress.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ByteBufferUtilTest {
    private static ByteBuffer hex(String hex) {
        return ByteBufferUtil.hexToBytes(hex);
    }

    @ParameterizedTest
    @CsvSource({
        "'', '', 0",
        "01, 01, 0",
        "01, 02, -1",
        "02, 01, 1",
        "ff, 01, 254",
        "01, ff, -254",
        "0102, 01, 1",
        "01, 0102, -1",
        "'', 00, -1",
        "0001ff, 0002, -1",
    })
    void comparesAsUnsignedBytes(String left, String right, int expected) {
        assertEquals(expected, ByteBufferUtil.compareUnsigned(hex(left), hex(right)));
    }

    @Test
    void comparesFromThePositionOfEachBuffer() {
        ByteBuffer left = hex("ff0102");
        ByteBuffer right = hex("0102");
        left.position(1);
        assertEquals(0, ByteBufferUtil.compareUnsigned(left, right));
        assertEquals(1, left.position());
    }

    @Test
    void convertsHexBothWays() {
        assertEquals("00ff7f80", ByteBufferUtil.bytesToHex(hex("00FF7F80")));
    }

    @Test
    void readsBytesAndAdvancesTheSource() {
        ByteBuffer source = hex("0102030405");
        ByteBuffer read = ByteBufferUtil.readBytes(source, 2);
        assertEquals("0102", ByteBufferUtil.bytesToHex(read));
        assertEquals(2, source.position());
    }

    @Test
    void copiesTheRemainingBytes() {
        ByteBuffer direct =
                ByteBuffer.allocateDirect(3).put(new byte[] {7, 8, 9}).flip();
        direct.position(1);
        assertEquals("0809", ByteBufferUtil.bytesToHex(ByteBuffer.wrap(ByteBufferUtil.getArray(direct))));
    }
}
