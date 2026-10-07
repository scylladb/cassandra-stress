package org.apache.cassandra.stress.driver.v4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultProgrammaticDriverConfigLoaderBuilder;
import java.util.HashMap;
import java.util.Map;
import org.apache.cassandra.stress.settings.AuthProvider;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.ProtocolVersion;
import org.apache.cassandra.stress.settings.SettingsNode;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class V4DriverConfigTest {
    private static SettingsNode node(String... params) {
        Map<String, String[]> args = new HashMap<>();
        if (params.length > 0) args.put("-node", params);
        return SettingsNode.get(args);
    }

    private static DriverExecutionProfile config(SettingsNode node) {
        return V4DriverConfig.loadBalancing(new DefaultProgrammaticDriverConfigLoaderBuilder(), node)
                .build()
                .getInitialConfig()
                .getDefaultProfile();
    }

    @ParameterizedTest
    @EnumSource(ConsistencyLevel.class)
    void mapsEachConsistencyLevelToTheSameName(ConsistencyLevel level) {
        assertEquals(level.name(), V4DriverConfig.consistency(level).name());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void mapsNumberedProtocolVersions(int version) {
        assertEquals(
                version,
                V4DriverConfig.protocolVersion(ProtocolVersion.fromInt(version)).getCode());
    }

    @Test
    void defaultProtocolLetsTheDriverNegotiate() {
        assertNull(V4DriverConfig.protocolVersion(ProtocolVersion.fromInt(-1)));
    }

    @Test
    void rejectsAProtocolTheDriverDoesNotSupport() {
        assertThrows(IllegalArgumentException.class, () -> V4DriverConfig.protocolVersion(ProtocolVersion.fromInt(9)));
    }

    @Test
    void setsTheCompression() {
        DriverExecutionProfile none = V4DriverConfig.compression(
                        new DefaultProgrammaticDriverConfigLoaderBuilder(), ProtocolCompression.NONE)
                .build()
                .getInitialConfig()
                .getDefaultProfile();
        DriverExecutionProfile lz4 = V4DriverConfig.compression(
                        new DefaultProgrammaticDriverConfigLoaderBuilder(), ProtocolCompression.LZ4)
                .build()
                .getInitialConfig()
                .getDefaultProfile();
        assertEquals("none", none.getString(DefaultDriverOption.PROTOCOL_COMPRESSION, "none"));
        assertEquals("lz4", lz4.getString(DefaultDriverOption.PROTOCOL_COMPRESSION));
    }

    @Test
    void dcAwareSetsTheLocalDatacenterAndTheRemoteFailover() {
        DriverExecutionProfile profile = config(node("datacenter=dc1", "rack=r1", "loadbalance=dc", "remote-dc=2"));
        assertEquals("dc1", profile.getString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER));
        assertEquals(2, profile.getInt(DefaultDriverOption.LOAD_BALANCING_DC_FAILOVER_MAX_NODES_PER_REMOTE_DC));
        assertFalse(profile.isDefined(DefaultDriverOption.LOAD_BALANCING_LOCAL_RACK));
    }

    @Test
    void rackAwareSetsTheLocalRack() {
        DriverExecutionProfile profile = config(node("datacenter=dc1", "rack=r1"));
        assertEquals("dc1", profile.getString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER));
        assertEquals("r1", profile.getString(DefaultDriverOption.LOAD_BALANCING_LOCAL_RACK));
    }

    @Test
    void roundRobinUsesEveryNodeAsLocal() {
        DriverExecutionProfile profile = config(node("datacenter=dc1", "loadbalance=rr"));
        assertEquals("BasicLoadBalancingPolicy", profile.getString(DefaultDriverOption.LOAD_BALANCING_POLICY_CLASS));
        assertFalse(profile.isDefined(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER));
    }

    @Test
    void rejectsAnUnknownAuthProvider() {
        assertThrows(
                IllegalArgumentException.class,
                () -> V4DriverConfig.auth(
                        com.datastax.oss.driver.api.core.CqlSession.builder(),
                        new AuthProvider("org.example.Unknown"),
                        "u",
                        "p"));
    }
}
