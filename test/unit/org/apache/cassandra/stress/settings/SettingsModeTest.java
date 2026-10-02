package org.apache.cassandra.stress.settings;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SettingsModeTest
{
    private static SettingsMode parse(String... params)
    {
        Map<String, String[]> clArgs = new HashMap<>();
        clArgs.put("-mode", params);
        return SettingsMode.get(clArgs);
    }

    private static void assertRemoved(String mode, String... params)
    {
        try
        {
            parse(params);
            fail("-mode " + String.join(" ", params) + " must be rejected");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Mode " + mode + " was removed. Use -mode native or -mode 4x.", e.getMessage());
        }
    }

    @Test
    public void thriftModeIsRejected()
    {
        assertRemoved("thrift", "thrift");
    }

    @Test
    public void smartThriftModeIsRejected()
    {
        assertRemoved("thrift", "thrift", "smart");
    }

    @Test
    public void nativeModeUsesDriver3()
    {
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, parse("cql3", "native").api);
    }

    @Test
    public void fourXModeUsesDriver4()
    {
        assertEquals(ConnectionAPI.JAVA_DRIVER4_NATIVE, parse("cql3", "4x").api);
    }
}
