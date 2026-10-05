package org.apache.cassandra.stress.generate.values;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import java.util.List;
import org.apache.cassandra.stress.util.codecs.EpochDayCodec;
import org.apache.cassandra.stress.util.codecs.NanoOfDayCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StoredValuesTest {
    private static GeneratorConfig config(String name) {
        return new GeneratorConfig("seed for stress" + name, null, null, null);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 37, 19675, -1, -719162})
    void readsADateThatTheDriverWrote(int days) {
        LocalDates dates = new LocalDates("day", config("day"));
        assertEquals(days, dates.read(new EpochDayCodec().encode(days, ProtocolVersion.V4)));
    }

    @Test
    void readsAListOfDatesThatTheDriverWrote() {
        Lists<Integer> lists = new Lists<>("days", new LocalDates("days", config("days")), config("days"));
        List<Integer> days = List.of(3, 40, 19675);
        assertEquals(days, lists.read(TypeCodecs.listOf(new EpochDayCodec()).encode(days, ProtocolVersion.V4)));
    }

    @Test
    void readsATimeThatTheDriverWrote() {
        Times times = new Times("at", config("at"));
        assertEquals(45_296_789L, times.read(new NanoOfDayCodec().encode(45_296_789L, ProtocolVersion.V4)));
    }

    @Test
    void readsATextAsItIs() {
        Strings strings = new Strings("label", config("label"));
        assertEquals("abc", strings.read(TypeCodecs.TEXT.encode("abc", ProtocolVersion.V4)));
    }
}
