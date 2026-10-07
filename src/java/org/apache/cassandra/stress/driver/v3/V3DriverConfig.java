// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v3;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PlainTextAuthProvider;
import com.datastax.driver.core.ProtocolOptions;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.RackAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.datastax.driver.core.policies.WhiteListPolicy;
import java.net.InetSocketAddress;
import java.util.List;
import org.apache.cassandra.stress.settings.AuthProvider;
import org.apache.cassandra.stress.settings.LoadBalanceType;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.SettingsNode;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.WhiteListAddresses;

final class V3DriverConfig {
    private V3DriverConfig() {}

    static com.datastax.driver.core.ConsistencyLevel consistency(ConsistencyLevel level) {
        return com.datastax.driver.core.ConsistencyLevel.valueOf(level.name());
    }

    static ProtocolVersion protocolVersion(org.apache.cassandra.stress.settings.ProtocolVersion version) {
        if (version.isDefault()) {
            return null;
        }
        return ProtocolVersion.fromInt(version.number());
    }

    static ProtocolOptions.Compression compression(ProtocolCompression compression) {
        return switch (compression) {
            case NONE -> ProtocolOptions.Compression.NONE;
            case LZ4 -> ProtocolOptions.Compression.LZ4;
            case SNAPPY -> ProtocolOptions.Compression.SNAPPY;
        };
    }

    static LoadBalancingPolicy loadBalancing(SettingsNode node, List<HostAndPort> contactPoints) {
        LoadBalancingPolicy policy = switch (LoadBalanceType.of(node)) {
            case ROUND_ROBIN -> new RoundRobinPolicy();
            case DC_AWARE -> dcAware(node);
            case RACK_AWARE -> rackAware(node);
        };
        if (node.isWhiteList) {
            List<InetSocketAddress> allowed = WhiteListAddresses.resolve(contactPoints).stream()
                    .flatMap(address -> ports(contactPoints).stream().map(port -> new InetSocketAddress(address, port)))
                    .toList();
            policy = new WhiteListPolicy(policy, allowed);
        }
        return new TokenAwarePolicy(policy, TokenAwarePolicy.ReplicaOrdering.RANDOM);
    }

    private static List<Integer> ports(List<HostAndPort> contactPoints) {
        return contactPoints.stream().map(HostAndPort::port).distinct().toList();
    }

    private static LoadBalancingPolicy dcAware(SettingsNode node) {
        DCAwareRoundRobinPolicy.Builder builder = DCAwareRoundRobinPolicy.builder();
        if (node.datacenter != null) {
            builder.withLocalDc(node.datacenter);
        }
        if (node.usedHostsPerRemoteDc != null) {
            builder.withUsedHostsPerRemoteDc(node.usedHostsPerRemoteDc);
        }
        return builder.build();
    }

    private static LoadBalancingPolicy rackAware(SettingsNode node) {
        RackAwareRoundRobinPolicy.Builder builder = RackAwareRoundRobinPolicy.builder();
        if (node.datacenter != null) {
            builder.withLocalDc(node.datacenter);
        }
        if (node.rack != null) {
            builder.withLocalRack(node.rack);
        }
        return builder.build();
    }

    static Cluster.Builder auth(Cluster.Builder builder, AuthProvider provider, String username, String password) {
        if (provider.isSet()) {
            provider.requirePlainText();
            return builder.withAuthProvider(new PlainTextAuthProvider(username, password));
        }
        return username != null ? builder.withCredentials(username, password) : builder;
    }
}
