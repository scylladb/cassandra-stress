package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsMiscTest {
    @Test
    void printsTheThreeVersionLines() {
        assertEquals(
                "Version: 1.0.0\nscylla-java-driver: 3.11.5.18\nscylla-java-driver-4x: 4.19.2.1\n",
                SettingsMisc.versionLines("1.0.0", "3.11.5.18", "4.19.2.1"));
    }

    @Test
    void readsTheStressVersionFromTheBuild() {
        String version = SettingsMisc.stressVersion();
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?"), version);
    }

    @Test
    void readsTheDriverVersionsFromTheDriverJars() {
        assertEquals(com.datastax.driver.core.Cluster.getDriverVersion(), SettingsMisc.driver3Version());
        assertEquals(
                com.datastax.oss.driver.api.core.session.Session.OSS_DRIVER_COORDINATES
                        .getVersion()
                        .toString(),
                SettingsMisc.driver4Version());
    }
}
