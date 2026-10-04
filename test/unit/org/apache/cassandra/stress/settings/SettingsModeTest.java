package org.apache.cassandra.stress.settings;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsModeTest
{
    private static SettingsMode parse(String... params)
    {
        return SettingsMode.get(new HashMap<>(Map.of("-mode", params)));
    }

    @ParameterizedTest
    @CsvSource({ "thrift, thrift", "thrift, thrift smart", "simplenative, cql3 simplenative" })
    void removedModesAreRejected(String mode, String params)
    {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(params.split(" ")));
        assertEquals("Mode " + mode + " was removed. Use -mode cql3 native or -mode cql3 4x.", e.getMessage());
    }

    @Test
    void nativeModeUsesDriver3()
    {
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, parse("cql3", "native").api);
    }

    @Test
    void fourXModeUsesDriver4()
    {
        assertEquals(ConnectionAPI.JAVA_DRIVER4_NATIVE, parse("cql3", "4x").api);
    }

    @Test
    void defaultsToDriver3Prepared()
    {
        SettingsMode mode = SettingsMode.get(new HashMap<>());
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, mode.api);
        assertEquals(ConnectionStyle.CQL_PREPARED, mode.style);
    }

    @Test
    void unpreparedSelectsPlainCql()
    {
        assertEquals(ConnectionStyle.CQL, parse("cql3", "native", "unprepared").style);
    }
}
