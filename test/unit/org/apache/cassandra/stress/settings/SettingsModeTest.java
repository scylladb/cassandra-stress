package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsModeTest {
    private static SettingsMode parse(String... params) {
        return SettingsMode.get(new HashMap<>(Map.of("-mode", params)));
    }

    @Test
    void simpleNativeModeIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("cql3", "simplenative"));
        assertEquals("Mode simplenative was removed. Use -mode cql3 native or -mode cql3 4x.", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"native", "4x"})
    void acceptsEachDriverName(String driver) {
        assertEquals(ConnectionStyle.CQL_PREPARED, parse("cql3", driver).style);
    }

    @Test
    void acceptsCql3Alone() {
        assertEquals(ConnectionStyle.CQL_PREPARED, parse("cql3").style);
    }

    @Test
    void defaultsToPreparedStatements() {
        assertEquals(ConnectionStyle.CQL_PREPARED, SettingsMode.get(new HashMap<>()).style);
    }

    @Test
    void unpreparedSelectsPlainCql() {
        assertEquals(ConnectionStyle.CQL, parse("cql3", "native", "unprepared").style);
    }

    @ParameterizedTest
    @ValueSource(strings = {"protocolVersion=2", "protocolVersion=6", "bogus"})
    void rejectsAnUnsupportedValue(String param) {
        assertThrows(IllegalArgumentException.class, () -> parse("cql3", param));
    }
}
