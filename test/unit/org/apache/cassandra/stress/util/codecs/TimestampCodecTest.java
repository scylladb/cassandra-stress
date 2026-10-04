package org.apache.cassandra.stress.util.codecs;

import java.time.ZoneOffset;
import java.util.Date;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimestampCodecTest
{
    private final TimestampCodec codec = new TimestampCodec(ZoneOffset.UTC);

    @Test
    void encodesAndDecodesMilliseconds()
    {
        Date date = new Date(1_700_000_000_123L);
        assertEquals(date, codec.decode(codec.encode(date, ProtocolVersion.V4), ProtocolVersion.V4));
        assertNull(codec.encode(null, ProtocolVersion.V4));
        assertNull(codec.decode(null, ProtocolVersion.V4));
    }

    @Test
    void formatsAsQuotedIso8601()
    {
        assertEquals("'2023-11-14T22:13:20.123Z'", codec.format(new Date(1_700_000_000_123L)));
        assertEquals("NULL", codec.format(null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "'2023-11-14T22:13:20.123Z'", "'2023-11-14 22:13:20.123+0000'", "'2023-11-14T22:13:20.123'", "1700000000123" })
    void parsesEachSupportedForm(String literal)
    {
        assertEquals(1_700_000_000_123L, codec.parse(literal).getTime());
    }

    @Test
    void parsesNullLiterals()
    {
        assertNull(codec.parse(null));
        assertNull(codec.parse("NULL"));
        assertNull(codec.parse(""));
    }

    @Test
    void rejectsUnquotedAndUnknownForms()
    {
        assertThrows(IllegalArgumentException.class, () -> codec.parse("2023-11-14"));
        assertThrows(IllegalArgumentException.class, () -> codec.parse("'yesterday'"));
    }

    @Test
    void acceptsDatesOnly()
    {
        assertTrue(codec.accepts(new Date()));
        assertTrue(codec.accepts(Date.class));
        assertEquals(8, codec.serializedSize().orElseThrow());
    }
}
