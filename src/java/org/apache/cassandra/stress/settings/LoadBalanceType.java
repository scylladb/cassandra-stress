// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.RackAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import java.util.Locale;
import java.util.function.Function;

public enum LoadBalanceType {
    ROUND_ROBIN(settings -> new RoundRobinPolicy()),
    DC_AWARE(settings -> {
        DCAwareRoundRobinPolicy.Builder builder = DCAwareRoundRobinPolicy.builder();
        if (settings.node.datacenter != null) builder.withLocalDc(settings.node.datacenter);
        if (settings.node.usedHostsPerRemoteDc != null)
            builder.withUsedHostsPerRemoteDc(settings.node.usedHostsPerRemoteDc);
        return builder.build();
    }),
    RACK_AWARE(settings -> {
        RackAwareRoundRobinPolicy.Builder builder = RackAwareRoundRobinPolicy.builder();
        if (settings.node.datacenter != null) builder.withLocalDc(settings.node.datacenter);
        if (settings.node.rack != null) builder.withLocalRack(settings.node.rack);
        return builder.build();
    });

    private final Function<StressSettings, LoadBalancingPolicy> strategy;

    LoadBalanceType(Function<StressSettings, LoadBalancingPolicy> strategy) {
        this.strategy = strategy;
    }

    public LoadBalancingPolicy createPolicy(StressSettings settings) {
        return strategy.apply(settings);
    }

    public static LoadBalanceType fromString(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "rr", "roundrobin", "round-robin" -> ROUND_ROBIN;
            case "dc", "dc-aware" -> DC_AWARE;
            case "rack", "rack-aware" -> RACK_AWARE;
            default -> throw new IllegalArgumentException("Unknown load balance strategy: " + value);
        };
    }

    @Override
    public String toString() {
        return switch (this) {
            case ROUND_ROBIN -> "round-robin";
            case DC_AWARE -> "dc-aware";
            case RACK_AWARE -> "rack-aware";
        };
    }
}
