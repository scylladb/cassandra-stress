package org.apache.cassandra.stress.driver.v3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ProtocolOptions;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.RackAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.datastax.driver.core.policies.WhiteListPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.cassandra.stress.settings.AuthProvider;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.ProtocolVersion;
import org.apache.cassandra.stress.settings.SettingsNode;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.HostAndPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class V3DriverConfigTest {
    private static final List<HostAndPort> CONTACT_POINTS = List.of(new HostAndPort("127.0.0.1", 9042));

    private static SettingsNode node(String... params) {
        Map<String, String[]> args = new HashMap<>();
        if (params.length > 0) {
            args.put("-node", params);
        }
        return SettingsNode.get(args);
    }

    private static LoadBalancingPolicy child(SettingsNode node) {
        TokenAwarePolicy tokenAware =
                assertInstanceOf(TokenAwarePolicy.class, V3DriverConfig.loadBalancing(node, CONTACT_POINTS));
        return tokenAware.getChildPolicy();
    }

    @ParameterizedTest
    @EnumSource(ConsistencyLevel.class)
    void mapsEachConsistencyLevelToTheSameName(ConsistencyLevel level) {
        assertEquals(level.name(), V3DriverConfig.consistency(level).name());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void mapsNumberedProtocolVersions(int version) {
        assertEquals(
                version,
                V3DriverConfig.protocolVersion(ProtocolVersion.fromInt(version)).toInt());
    }

    @Test
    void defaultProtocolLetsTheDriverNegotiate() {
        assertNull(V3DriverConfig.protocolVersion(ProtocolVersion.fromInt(-1)));
    }

    @ParameterizedTest
    @CsvSource({"NONE, NONE", "LZ4, LZ4", "SNAPPY, SNAPPY"})
    void mapsTheCompression(ProtocolCompression compression, ProtocolOptions.Compression expected) {
        assertEquals(expected, V3DriverConfig.compression(compression));
    }

    @Test
    void defaultsToDcAwareAndToRackAwareWhenARackIsSet() {
        assertInstanceOf(DCAwareRoundRobinPolicy.class, child(node()));
        assertInstanceOf(RackAwareRoundRobinPolicy.class, child(node("rack=r1")));
        assertInstanceOf(RoundRobinPolicy.class, child(node("rack=r1", "loadbalance=rr")));
    }

    @Test
    void wrapsTheWhitelistedPolicy() {
        WhiteListPolicy whiteList = assertInstanceOf(WhiteListPolicy.class, child(node("whitelist")));
        assertInstanceOf(DCAwareRoundRobinPolicy.class, whiteList.getChildPolicy());
    }

    @Test
    void rejectsAnUnresolvableWhitelistedNode() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> V3DriverConfig.loadBalancing(
                        node("whitelist"), List.of(new HostAndPort("no-such-host.invalid", 9042))));
        assertEquals("Cannot resolve the whitelisted node no-such-host.invalid", e.getMessage());
    }

    @Test
    void rejectsAnUnknownAuthProvider() {
        assertThrows(
                IllegalArgumentException.class,
                () -> V3DriverConfig.auth(Cluster.builder(), new AuthProvider("org.example.Unknown"), "u", "p"));
    }
}
