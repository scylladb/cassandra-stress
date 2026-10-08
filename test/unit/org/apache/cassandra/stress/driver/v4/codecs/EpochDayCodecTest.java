package org.apache.cassandra.stress.driver.v4.codecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EpochDayCodecTest {
    private final EpochDayCodec codec = new EpochDayCodec();

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 19675, -1, -719162})
    void encodesTheSameBytesAsTheDriverDateCodec(int days) {
        assertEquals(
                TypeCodecs.DATE.encode(LocalDate.ofEpochDay(days), ProtocolVersion.V4),
                codec.encode(days, ProtocolVersion.V4));
        assertEquals(days, codec.decode(codec.encode(days, ProtocolVersion.V4), ProtocolVersion.V4));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 19675})
    void formatsAndParsesAQuotedDate(int days) {
        assertEquals("'" + LocalDate.ofEpochDay(days) + "'", codec.format(days));
        assertEquals(days, codec.parse(codec.format(days)));
    }

    @Test
    void mapsDateToInteger() {
        assertEquals(DataTypes.DATE, codec.getCqlType());
        assertTrue(codec.accepts(Integer.class));
        assertTrue(codec.accepts((Object) 3));
        assertNull(codec.encode(null, ProtocolVersion.V4));
        assertNull(codec.decode(null, ProtocolVersion.V4));
        assertEquals("NULL", codec.format(null));
    }
}
