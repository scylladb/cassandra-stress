// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Properties;
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
        return driverVersion("com/datastax/driver/core/Driver.properties");
    }

    public static String driver4Version() {
        return driverVersion("com/datastax/oss/driver/Driver.properties");
    }

    static String driverVersion(String resource) {
        try (InputStream in = StressClients.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing driver resource " + resource);
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("driver.version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
