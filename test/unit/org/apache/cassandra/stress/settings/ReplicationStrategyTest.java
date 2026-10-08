package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReplicationStrategyTest {
    @ParameterizedTest
    @CsvSource({
        "NetworkTopologyStrategy, org.apache.cassandra.locator.NetworkTopologyStrategy",
        "EverywhereStrategy, org.apache.cassandra.locator.EverywhereStrategy",
        "org.apache.cassandra.locator.NetworkTopologyStrategy, org.apache.cassandra.locator.NetworkTopologyStrategy",
    })
    void returnsTheFullName(String name, String fullName) {
        assertEquals(fullName, ReplicationStrategy.validate(name));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "SimpleStrategy",
                "org.apache.cassandra.locator.SimpleStrategy",
                "LocalStrategy",
                "OldNetworkTopologyStrategy",
                "java.lang.String"
            })
    void rejectsOtherStrategies(String name) {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> ReplicationStrategy.validate(name));
        assertEquals("Invalid replication strategy: " + name, e.getMessage());
    }
}
