package org.apache.cassandra.stress.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistencyLevelTest
{
    @ParameterizedTest
    @EnumSource(ConsistencyLevel.class)
    void mapsEachLevelToTheSameNameInBothDrivers(ConsistencyLevel level)
    {
        assertEquals(level.name(), level.ToV3Value().name());
        assertEquals(level.name(), level.ToV4Value().name());
    }

    @Test
    void marksSerialAndLocalLevels()
    {
        assertTrue(ConsistencyLevel.LOCAL_SERIAL.isSerialConsistency());
        assertFalse(ConsistencyLevel.QUORUM.isSerialConsistency());
        assertTrue(ConsistencyLevel.LOCAL_ONE.isDatacenterLocal());
        assertFalse(ConsistencyLevel.ONE.isDatacenterLocal());
    }
}
