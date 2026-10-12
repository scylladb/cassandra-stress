// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v4;

import java.util.List;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressClientProvider;
import org.apache.cassandra.stress.settings.ConnectionAPI;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.EncryptionOptions;

public final class V4ClientProvider implements StressClientProvider {
    @Override
    public ConnectionAPI api() {
        return ConnectionAPI.JAVA_DRIVER4_NATIVE;
    }

    @Override
    public String name() {
        return "scylla-java-driver-4x";
    }

    @Override
    public String version() {
        return StressClientProvider.driverVersion(V4ClientProvider.class, "com/datastax/oss/driver/Driver.properties");
    }

    @Override
    public StressClient create(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        return new JavaDriverV4Client(settings, hosts, port, encryptionOptions);
    }
}
