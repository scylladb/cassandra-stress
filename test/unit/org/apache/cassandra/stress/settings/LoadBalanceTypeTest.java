package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultProgrammaticDriverConfigLoaderBuilder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class LoadBalanceTypeTest {
    @ParameterizedTest
    @CsvSource({
        "RR, ROUND_ROBIN",
        "roundrobin, ROUND_ROBIN",
        "round-robin, ROUND_ROBIN",
        "dc, DC_AWARE",
        "dc-aware, DC_AWARE",
        "rack, RACK_AWARE",
        "Rack-Aware, RACK_AWARE"
    })
    void readsEachAlias(String alias, LoadBalanceType expected) {
        assertEquals(expected, LoadBalanceType.fromString(alias));
    }

    @Test
    void readsNullAsUnset() {
        assertNull(LoadBalanceType.fromString(null));
    }

    @ParameterizedTest
    @EnumSource(LoadBalanceType.class)
    void printsTheNameItReads(LoadBalanceType type) {
        assertEquals(type, LoadBalanceType.fromString(type.toString()));
    }

    @Test
    void rejectsAnUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> LoadBalanceType.fromString("random"));
    }

    private static SettingsNode node(String... params) {
        Map<String, String[]> args = new HashMap<>();
        if (params.length > 0) args.put("-node", params);
        return SettingsNode.get(args);
    }

    private static DriverExecutionProfile config(SettingsNode node) {
        return LoadBalanceType.of(node)
                .applyTo(new DefaultProgrammaticDriverConfigLoaderBuilder(), node)
                .build()
                .getInitialConfig()
                .getDefaultProfile();
    }

    @Test
    void defaultsToDcAwareAndToRackAwareWhenARackIsSet() {
        assertEquals(LoadBalanceType.DC_AWARE, LoadBalanceType.of(node()));
        assertEquals(LoadBalanceType.RACK_AWARE, LoadBalanceType.of(node("rack=r1")));
        assertEquals(LoadBalanceType.ROUND_ROBIN, LoadBalanceType.of(node("rack=r1", "loadbalance=rr")));
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
}
