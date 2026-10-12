package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressClientProvider;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.junit.jupiter.api.Test;

class SettingsMiscTest {
    @Test
    void printsTheVersionLines() {
        assertEquals(
                "Version: 1.0.0\nscylla-java-driver: 3.11.5.19\nscylla-java-driver-4x: 4.19.2.2\n",
                SettingsMisc.versionLines(
                        "1.0.0",
                        List.of(
                                new FixedProvider("scylla-java-driver", "3.11.5.19"),
                                new FixedProvider("scylla-java-driver-4x", "4.19.2.2"))));
    }

    @Test
    void readsTheStressVersionFromTheBuild() {
        String version = SettingsMisc.stressVersion();
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?"), version);
    }

    private record FixedProvider(String name, String version) implements StressClientProvider {
        @Override
        public ConnectionAPI api() {
            return ConnectionAPI.JAVA_DRIVER_NATIVE;
        }

        @Override
        public StressClient create(
                StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
            throw new UnsupportedOperationException();
        }
    }
}
