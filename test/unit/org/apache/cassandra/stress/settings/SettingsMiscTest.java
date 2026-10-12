package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsMiscTest {
    @Test
    void printsTheVersionLines() {
        assertEquals(
                "Version: 1.0.0\nscylla-java-driver: 3.11.5.19\nscylla-java-driver-4x: 4.19.2.2\n",
                SettingsMisc.versionLines("1.0.0", "3.11.5.19", "4.19.2.2"));
    }

    @Test
    void readsTheStressVersionFromTheBuild() {
        String version = SettingsMisc.stressVersion();
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?"), version);
    }
}
