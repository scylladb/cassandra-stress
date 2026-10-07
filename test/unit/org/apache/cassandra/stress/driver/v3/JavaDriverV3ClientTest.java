package org.apache.cassandra.stress.driver.v3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.TypeCodec;
import com.google.common.reflect.TypeToken;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JavaDriverV3ClientTest {
    private final CodecRegistry registry = JavaDriverV3Client.codecRegistry();

    @Test
    void bindsGeneratedDatesTimesAndTimestamps() {
        assertEquals(
                TypeCodec.date().serialize(LocalDate.fromDaysSinceEpoch(42), ProtocolVersion.V4),
                registry.codecFor(DataType.date(), 42).serialize(42, ProtocolVersion.V4));
        assertEquals(
                TypeCodec.time().serialize(7L, ProtocolVersion.V4),
                registry.codecFor(DataType.time(), 7L).serialize(7L, ProtocolVersion.V4));
        assertEquals(
                TypeCodec.timestamp().serialize(new Date(5L), ProtocolVersion.V4),
                registry.codecFor(DataType.timestamp(), new Date(5L)).serialize(new Date(5L), ProtocolVersion.V4));
    }

    @Test
    void bindsGeneratedCollectionsOfDates() {
        Set<Integer> days = new LinkedHashSet<>(List.of(1, 2));
        Set<LocalDate> dates =
                new LinkedHashSet<>(List.of(LocalDate.fromDaysSinceEpoch(1), LocalDate.fromDaysSinceEpoch(2)));
        TypeCodec<Set<LocalDate>> driverCodec = registry.codecFor(DataType.set(DataType.date()), dates);
        TypeCodec<Set<Integer>> generatedCodec =
                registry.codecFor(DataType.set(DataType.date()), new TypeToken<Set<Integer>>() {});
        assertEquals(
                driverCodec.serialize(dates, ProtocolVersion.V4), generatedCodec.serialize(days, ProtocolVersion.V4));
    }

    @Test
    void readsTheDriverVersion() {
        assertEquals(com.datastax.driver.core.Cluster.getDriverVersion(), JavaDriverV3Client.driverVersion());
    }
}
