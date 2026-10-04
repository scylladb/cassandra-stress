package org.apache.cassandra.stress.settings;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

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

    @Test
    void jmxPortIsRejected()
    {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("jmx=6868"));
        assertEquals("Port option jmx= was removed. Use -port native=.", e.getMessage());
    }
}
