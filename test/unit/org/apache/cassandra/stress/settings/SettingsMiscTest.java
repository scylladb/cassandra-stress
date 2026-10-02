package org.apache.cassandra.stress.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SettingsMiscTest
{
    @Test
    public void printsTheThreeVersionLines()
    {
        assertEquals("Version: 1.0.0\nscylla-java-driver: 3.11.5.18\nscylla-java-driver-4x: 4.19.2.1\n",
                     SettingsMisc.versionLines("1.0.0", "3.11.5.18", "4.19.2.1"));
    }

    @Test
    public void readsTheDriverVersionsFromTheDriverJars()
    {
        assertEquals(com.datastax.driver.core.Cluster.getDriverVersion(), SettingsMisc.driver3Version());
        assertEquals(shaded.com.datastax.oss.driver.api.core.session.Session.OSS_DRIVER_COORDINATES.getVersion().toString(),
                     SettingsMisc.driver4Version());
    }
}
