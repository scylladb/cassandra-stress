// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import java.util.Locale;

public enum LoadBalanceType {
    ROUND_ROBIN,
    DC_AWARE,
    RACK_AWARE;

    public static LoadBalanceType of(SettingsNode node) {
        if (node.loadBalance != null) return node.loadBalance;
        return node.rack != null ? RACK_AWARE : DC_AWARE;
    }

    public ProgrammaticDriverConfigLoaderBuilder applyTo(
            ProgrammaticDriverConfigLoaderBuilder builder, SettingsNode node) {
        if (this == ROUND_ROBIN)
            return builder.withString(DefaultDriverOption.LOAD_BALANCING_POLICY_CLASS, "BasicLoadBalancingPolicy");
        if (node.datacenter != null)
            builder = builder.withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, node.datacenter);
        if (this == RACK_AWARE && node.rack != null)
            builder = builder.withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_RACK, node.rack);
        if (this == DC_AWARE && node.usedHostsPerRemoteDc != null)
            builder = builder.withInt(
                    DefaultDriverOption.LOAD_BALANCING_DC_FAILOVER_MAX_NODES_PER_REMOTE_DC, node.usedHostsPerRemoteDc);
        return builder;
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
