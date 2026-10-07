package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void defaultsToDcAwareAndToRackAwareWhenARackIsSet() {
        assertEquals(LoadBalanceType.DC_AWARE, LoadBalanceType.of(node()));
        assertEquals(LoadBalanceType.RACK_AWARE, LoadBalanceType.of(node("rack=r1")));
        assertEquals(LoadBalanceType.ROUND_ROBIN, LoadBalanceType.of(node("rack=r1", "loadbalance=rr")));
    }
}
