package org.apache.cassandra.stress.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaDriverClientTest {
    private final CodecRegistry registry = JavaDriverClient.codecRegistry();

    @Test
    void bindsGeneratedDatesTimesAndTimestamps() {
        assertEquals(
                TypeCodecs.DATE.encode(LocalDate.ofEpochDay(42), ProtocolVersion.V4),
                registry.codecFor(DataTypes.DATE, 42).encode(42, ProtocolVersion.V4));
        assertEquals(
                TypeCodecs.TIME.encode(LocalTime.ofNanoOfDay(7L), ProtocolVersion.V4),
                registry.codecFor(DataTypes.TIME, 7L).encode(7L, ProtocolVersion.V4));
        assertEquals(
                TypeCodecs.TIMESTAMP.encode(new Date(5L).toInstant(), ProtocolVersion.V4),
                registry.codecFor(DataTypes.TIMESTAMP, new Date(5L)).encode(new Date(5L), ProtocolVersion.V4));
    }

    @Test
    void bindsGeneratedCollectionsOfDates() {
        LinkedHashSet<Integer> days = new LinkedHashSet<>(List.of(1, 2));
        LinkedHashSet<LocalDate> dates = new LinkedHashSet<>(List.of(LocalDate.ofEpochDay(1), LocalDate.ofEpochDay(2)));
        assertEquals(
                registry.codecFor(DataTypes.setOf(DataTypes.DATE), dates).encode(dates, ProtocolVersion.V4),
                registry.codecFor(DataTypes.setOf(DataTypes.DATE), days).encode(days, ProtocolVersion.V4));
    }

    @Test
    void keepsTheDriverCodecsForDriverTypes() {
        assertEquals(TypeCodecs.DATE, registry.codecFor(DataTypes.DATE, LocalDate.ofEpochDay(1)));
        assertEquals(TypeCodecs.BIGINT, registry.codecFor(DataTypes.BIGINT, 1L));
    }
}
