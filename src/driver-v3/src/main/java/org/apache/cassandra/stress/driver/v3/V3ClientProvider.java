// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v3;

import java.util.List;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressClientProvider;
import org.apache.cassandra.stress.settings.ConnectionAPI;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.EncryptionOptions;

public final class V3ClientProvider implements StressClientProvider {
    @Override
    public ConnectionAPI api() {
        return ConnectionAPI.JAVA_DRIVER_NATIVE;
    }

    @Override
    public String name() {
        return "scylla-java-driver";
    }

    @Override
    public String version() {
        return StressClientProvider.driverVersion(V3ClientProvider.class, "com/datastax/driver/core/Driver.properties");
    }

    @Override
    public StressClient create(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        return new JavaDriverV3Client(settings, hosts, port, encryptionOptions);
    }
}
