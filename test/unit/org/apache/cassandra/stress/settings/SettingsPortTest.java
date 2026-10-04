package org.apache.cassandra.stress.settings;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsPortTest
{
    private static SettingsPort parse(String... params)
    {
        return SettingsPort.get(new HashMap<>(Map.of("-port", params)));
    }

    @Test
    void readsTheNativePort()
    {
        assertEquals(19042, parse("native=19042").nativePort);
    }

    @Test
    void defaultsTo9042()
    {
        assertEquals(9042, SettingsPort.get(new HashMap<>()).nativePort);
    }

    @ParameterizedTest
    @CsvSource({ "jmx=, jmx=6868", "thrift=, thrift=9160" })
    void removedPortsAreRejected(String option, String param)
    {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(param));
        assertEquals("Port option " + option + " was removed. Use -port native=.", e.getMessage());
    }
}
