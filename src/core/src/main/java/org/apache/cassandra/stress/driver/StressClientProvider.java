// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Properties;
import org.apache.cassandra.stress.settings.ConnectionAPI;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.EncryptionOptions;

/**
 * Creates the clients of one driver. Each driver module registers an
 * implementation as a service, and {@link StressClients} finds it through
 * {@link java.util.ServiceLoader}.
 */
public interface StressClientProvider {
    /** The connection API that selects this driver. */
    ConnectionAPI api();

    /** The name of the driver in the output of the version command. */
    String name();

    /** The version of the driver library. */
    String version();

    StressClient create(StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions);

    /**
     * Reads the driver.version property of a driver jar resource. The driver
     * classes stay uninitialized, so the version command prints no driver log.
     */
    static String driverVersion(Class<?> anchor, String resource) {
        try (InputStream in = anchor.getClassLoader().getResourceAsStream(resource)) {
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
