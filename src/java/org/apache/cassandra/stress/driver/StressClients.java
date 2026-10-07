// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.util.List;
import org.apache.cassandra.stress.driver.v3.JavaDriverV3Client;
import org.apache.cassandra.stress.driver.v4.JavaDriverV4Client;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.EncryptionOptions;

public final class StressClients {
    private StressClients() {}

    public static StressClient create(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        return switch (settings.mode.api) {
            case JAVA_DRIVER_NATIVE -> new JavaDriverV3Client(settings, hosts, port, encryptionOptions);
            case JAVA_DRIVER4_NATIVE -> new JavaDriverV4Client(settings, hosts, port, encryptionOptions);
        };
    }

    public static String driver3Version() {
        return JavaDriverV3Client.driverVersion();
    }

    public static String driver4Version() {
        return JavaDriverV4Client.driverVersion();
    }
}
