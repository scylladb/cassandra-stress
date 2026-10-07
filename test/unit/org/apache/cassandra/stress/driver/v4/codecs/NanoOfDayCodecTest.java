package org.apache.cassandra.stress.util.codecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import java.time.DateTimeException;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NanoOfDayCodecTest {
    private final NanoOfDayCodec codec = new NanoOfDayCodec();

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 45_296_789_000_000L, 86_399_999_999_999L})
    void encodesTheSameBytesAsTheDriverTimeCodec(long nanos) {
        assertEquals(
                TypeCodecs.TIME.encode(LocalTime.ofNanoOfDay(nanos), ProtocolVersion.V4),
                codec.encode(nanos, ProtocolVersion.V4));
        assertEquals(nanos, codec.decode(codec.encode(nanos, ProtocolVersion.V4), ProtocolVersion.V4));
        assertEquals(nanos, codec.parse(codec.format(nanos)));
    }

    @Test
    void mapsTimeToLong() {
        assertEquals(DataTypes.TIME, codec.getCqlType());
        assertTrue(codec.accepts(Long.class));
        assertTrue(codec.accepts((Object) 3L));
        assertNull(codec.encode(null, ProtocolVersion.V4));
        assertNull(codec.decode(null, ProtocolVersion.V4));
        assertEquals("NULL", codec.format(null));
    }

    @Test
    void rejectsAValueOutsideOneDay() {
        assertThrows(DateTimeException.class, () -> codec.encode(86_400_000_000_000L, ProtocolVersion.V4));
    }
}
