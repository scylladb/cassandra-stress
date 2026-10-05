package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.oss.driver.api.core.session.Session;
import org.junit.jupiter.api.Test;

class SettingsMiscTest {
    @Test
    void printsTheVersionLines() {
        assertEquals(
                "Version: 1.0.0\nscylla-java-driver: 4.19.2.1\nscylla-java-driver-4x: 4.19.2.1\n",
                SettingsMisc.versionLines("1.0.0", "4.19.2.1"));
    }

    @Test
    void readsTheStressVersionFromTheBuild() {
        String version = SettingsMisc.stressVersion();
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?"), version);
    }

    @Test
    void readsTheDriverVersionFromTheDriverJar() {
        assertEquals(Session.OSS_DRIVER_COORDINATES.getVersion().toString(), SettingsMisc.driverVersion());
    }
}
