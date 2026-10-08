// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v4;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.auth.ProgrammaticPlainTextAuthProvider;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import org.apache.cassandra.stress.settings.AuthProvider;
import org.apache.cassandra.stress.settings.LoadBalanceType;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.SettingsNode;
import org.apache.cassandra.stress.util.ConsistencyLevel;

final class V4DriverConfig {
    private V4DriverConfig() {}

    static com.datastax.oss.driver.api.core.ConsistencyLevel consistency(ConsistencyLevel level) {
        return DefaultConsistencyLevel.valueOf(level.name());
    }

    static ProtocolVersion protocolVersion(org.apache.cassandra.stress.settings.ProtocolVersion version) {
        if (version.isDefault()) {
            return null;
        }
        return switch (version.number()) {
            case 3 -> ProtocolVersion.V3;
            case 4 -> ProtocolVersion.V4;
            case 5 -> ProtocolVersion.V5;
            default -> throw new IllegalArgumentException("Invalid protocol version: " + version);
        };
    }

    static ProgrammaticDriverConfigLoaderBuilder compression(
            ProgrammaticDriverConfigLoaderBuilder builder, ProtocolCompression compression) {
        if (compression == ProtocolCompression.NONE) {
            return builder;
        }
        return builder.withString(DefaultDriverOption.PROTOCOL_COMPRESSION, compression.protocolName());
    }

    static ProgrammaticDriverConfigLoaderBuilder loadBalancing(
            ProgrammaticDriverConfigLoaderBuilder builder, SettingsNode node) {
        LoadBalanceType type = LoadBalanceType.of(node);
        if (type == LoadBalanceType.ROUND_ROBIN) {
            return builder.withString(DefaultDriverOption.LOAD_BALANCING_POLICY_CLASS, "BasicLoadBalancingPolicy");
        }
        if (node.datacenter != null) {
            builder = builder.withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, node.datacenter);
        }
        if (type == LoadBalanceType.RACK_AWARE && node.rack != null) {
            builder = builder.withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_RACK, node.rack);
        }
        if (type == LoadBalanceType.DC_AWARE && node.usedHostsPerRemoteDc != null) {
            builder = builder.withInt(
                    DefaultDriverOption.LOAD_BALANCING_DC_FAILOVER_MAX_NODES_PER_REMOTE_DC, node.usedHostsPerRemoteDc);
        }
        return builder;
    }

    static CqlSessionBuilder auth(CqlSessionBuilder builder, AuthProvider provider, String username, String password) {
        if (provider.isSet()) {
            provider.requirePlainText();
            return builder.withAuthProvider(new ProgrammaticPlainTextAuthProvider(username, password));
        }
        return username != null ? builder.withCredentials(username, password) : builder;
    }
}
