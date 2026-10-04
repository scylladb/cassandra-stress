package org.apache.cassandra.stress.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MurmurHashTest
{
    private static ByteBuffer bytes(String value)
    {
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    private static long[] hash3(String value, long seed)
    {
        ByteBuffer buffer = bytes(value);
        long[] result = new long[2];
        MurmurHash.hash3_x64_128(buffer, buffer.position(), buffer.remaining(), seed, result);
        return result;
    }

    @Test
    void hash3MatchesTheReferenceVectors()
    {
        assertArrayEquals(new long[]{ 0, 0 }, hash3("", 0));
        assertArrayEquals(new long[]{ 0xcbd8a7b341bd9b02L, 0x5b1e906a48ae1d19L }, hash3("hello", 0));
        assertArrayEquals(new long[]{ 0xe34bbc7bbc071b6cL, 0x7a433ca9c49a9347L }, hash3("The quick brown fox jumps over the lazy dog", 0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "a", "ab", "abcdefg", "abcdefgh", "abcdefghijklmno", "abcdefghijklmnop", "abcdefghijklmnopq", "zażółć gęślą jaźń" })
    void everyTailLengthIsStableAndSeeded(String value)
    {
        assertArrayEquals(hash3(value, 7), hash3(value, 7));
        assertEquals(MurmurHash.hash32(bytes(value), 0, bytes(value).remaining(), 7), MurmurHash.hash32(bytes(value), 0, bytes(value).remaining(), 7));
        assertEquals(MurmurHash.hash2_64(bytes(value), 0, bytes(value).remaining(), 7), MurmurHash.hash2_64(bytes(value), 0, bytes(value).remaining(), 7));
    }

    @Test
    void hashesRespectTheOffset()
    {
        ByteBuffer padded = bytes("xxhello");
        long[] result = new long[2];
        MurmurHash.hash3_x64_128(padded, 2, 5, 0, result);
        assertArrayEquals(hash3("hello", 0), result);
        assertEquals(MurmurHash.hash32(bytes("hello"), 0, 5, 1), MurmurHash.hash32(padded, 2, 5, 1));
        assertEquals(MurmurHash.hash2_64(bytes("hello"), 0, 5, 1), MurmurHash.hash2_64(padded, 2, 5, 1));
    }
}
