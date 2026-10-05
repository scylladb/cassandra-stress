package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsModeTest {
    private static SettingsMode parse(String... params) {
        return SettingsMode.get(new HashMap<>(Map.of("-mode", params)));
    }

    @Test
    void simpleNativeModeIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("cql3", "simplenative"));
        assertEquals("Mode simplenative was removed. Use -mode cql3 native or -mode cql3 4x.", e.getMessage());
    }

    @Test
    void nativeModeUsesDriver3() {
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, parse("cql3", "native").api);
    }

    @Test
    void fourXModeUsesDriver4() {
        assertEquals(ConnectionAPI.JAVA_DRIVER4_NATIVE, parse("cql3", "4x").api);
    }

    @Test
    void defaultsToDriver3Prepared() {
        SettingsMode mode = SettingsMode.get(new HashMap<>());
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, mode.api);
        assertEquals(ConnectionStyle.CQL_PREPARED, mode.style);
    }

    @Test
    void unpreparedSelectsPlainCql() {
        assertEquals(ConnectionStyle.CQL, parse("cql3", "native", "unprepared").style);
    }
}
