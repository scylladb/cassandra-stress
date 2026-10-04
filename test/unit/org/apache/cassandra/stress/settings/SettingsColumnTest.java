package org.apache.cassandra.stress.settings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsColumnTest
{
    private static SettingsColumn parse(String... params)
    {
        return SettingsColumn.get(new HashMap<>(Map.of("-col", params)));
    }

    @Test
    void countGeneratesSortedNames()
    {
        assertEquals(List.of("C0", "C1", "C2"), parse("n=FIXED(3)").namestrs);
    }

    @Test
    void namesAreSorted()
    {
        assertEquals(List.of("a", "b"), parse("names=b,a").namestrs);
    }

    @ParameterizedTest
    @ValueSource(strings = { "super=1", "comparator=UTF8Type" })
    void removedOptionsAreRejected(String param)
    {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(param));
        assertEquals("Invalid parameter " + param, e.getMessage());
    }
}
