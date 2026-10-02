package org.apache.cassandra.stress.settings;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SettingsNodeTest
{
    private static SettingsNode parse(String... params)
    {
        Map<String, String[]> clArgs = new HashMap<>();
        clArgs.put("-node", params);
        return SettingsNode.get(clArgs);
    }

    @Test
    public void readsRemoteDc()
    {
        assertEquals(Integer.valueOf(5), parse("remote-dc=5").usedHostsPerRemoteDc);
    }

    @Test
    public void leavesRemoteDcUnsetByDefault()
    {
        assertNull(parse().usedHostsPerRemoteDc);
    }

    @Test
    public void readsRemoteDcWithOtherOptions()
    {
        SettingsNode settings = parse("datacenter=dc1", "remote-dc=3", "localhost");
        assertEquals("dc1", settings.datacenter);
        assertEquals(Integer.valueOf(3), settings.usedHostsPerRemoteDc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroRemoteDc()
    {
        parse("remote-dc=0");
    }
}
