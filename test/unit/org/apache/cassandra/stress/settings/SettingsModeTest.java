package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @Test
    void nativeSelectsDriver3AndIsTheDefault() {
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, parse("cql3", "native").api);
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, parse("cql3").api);
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, SettingsMode.get(new HashMap<>()).api);
    }

    @Test
    void fourXSelectsDriver4() {
        assertEquals(ConnectionAPI.JAVA_DRIVER4_NATIVE, parse("cql3", "4x").api);
    }

    @ParameterizedTest
    @CsvSource({"native, JAVA_DRIVER_NATIVE", "4x, JAVA_DRIVER4_NATIVE"})
    void printsTheApiFirst(String driver, String api) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        parse("cql3", driver)
                .printSettings(new org.apache.cassandra.stress.util.MultiResultLogger(
                        new java.io.PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(
                "  API: " + api,
                bytes.toString(java.nio.charset.StandardCharsets.UTF_8)
                        .lines()
                        .findFirst()
                        .orElseThrow());
    }
}
