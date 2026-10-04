package org.apache.cassandra.stress.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadBalanceTypeTest
{
    @ParameterizedTest
    @CsvSource({ "RR, ROUND_ROBIN", "roundrobin, ROUND_ROBIN", "round-robin, ROUND_ROBIN", "dc, DC_AWARE",
                 "dc-aware, DC_AWARE", "rack, RACK_AWARE", "Rack-Aware, RACK_AWARE" })
    void readsEachAlias(String alias, LoadBalanceType expected)
    {
        assertEquals(expected, LoadBalanceType.fromString(alias));
    }

    @Test
    void readsNullAsUnset()
    {
        assertNull(LoadBalanceType.fromString(null));
    }

    @ParameterizedTest
    @EnumSource(LoadBalanceType.class)
    void printsTheNameItReads(LoadBalanceType type)
    {
        assertEquals(type, LoadBalanceType.fromString(type.toString()));
    }

    @Test
    void rejectsAnUnknownName()
    {
        assertThrows(IllegalArgumentException.class, () -> LoadBalanceType.fromString("random"));
    }
}
