package org.apache.cassandra.stress.settings;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SettingsColumnTest
{
    private static SettingsColumn parse(String... params)
    {
        Map<String, String[]> clArgs = new HashMap<>();
        clArgs.put("-col", params);
        return SettingsColumn.get(clArgs);
    }

    private static void assertRejected(String param)
    {
        try
        {
            parse(param);
            fail("-col " + param + " must be rejected");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Invalid parameter " + param, e.getMessage());
        }
    }

    @Test
    public void countGeneratesSortedNames()
    {
        assertEquals(Arrays.asList("C0", "C1", "C2"), parse("n=FIXED(3)").namestrs);
    }

    @Test
    public void namesAreSorted()
    {
        assertEquals(Arrays.asList("a", "b"), parse("names=b,a").namestrs);
    }

    @Test
    public void superColumnsAreRejected()
    {
        assertRejected("super=1");
    }

    @Test
    public void comparatorIsRejected()
    {
        assertRejected("comparator=UTF8Type");
    }
}
