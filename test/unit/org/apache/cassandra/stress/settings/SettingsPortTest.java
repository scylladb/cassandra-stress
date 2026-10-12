package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsPortTest {
    private static SettingsPort parse(String... params) {
        return SettingsPort.get(new HashMap<>(Map.of("-port", params)));
    }

    @Test
    void readsTheNativePort() {
        assertEquals(19042, parse("native=19042").nativePort);
    }

    @Test
    void defaultsTo9042() {
        assertEquals(9042, SettingsPort.get(new HashMap<>()).nativePort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jmx=6868", "JMX=7199", "Jmx=7199"})
    void jmxPortIsRejectedInAnyCase(String option) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(option));
        assertEquals("Port option jmx= was removed. Use -port native=.", e.getMessage());
    }

    @Test
    void thriftPortIsAnInvalidParameter() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("thrift=9160"));
        assertEquals("Invalid parameter thrift=9160", e.getMessage());
    }
}
