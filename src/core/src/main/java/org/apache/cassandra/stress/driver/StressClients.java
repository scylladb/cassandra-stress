// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import org.apache.cassandra.stress.settings.ConnectionAPI;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.EncryptionOptions;

public final class StressClients {
    private static final List<StressClientProvider> PROVIDERS = ServiceLoader.load(StressClientProvider.class).stream()
            .map(ServiceLoader.Provider::get)
            .sorted(Comparator.comparing(StressClientProvider::api))
            .toList();

    private StressClients() {}

    public static StressClient create(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        return provider(settings.mode.api).create(settings, hosts, port, encryptionOptions);
    }

    /** The driver providers in the order of {@link ConnectionAPI}. */
    public static List<StressClientProvider> providers() {
        return PROVIDERS;
    }

    public static StressClientProvider provider(ConnectionAPI api) {
        return PROVIDERS.stream()
                .filter(provider -> provider.api() == api)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No driver module provides the " + api + " API"));
    }
}
