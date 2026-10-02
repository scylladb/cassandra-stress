package org.apache.cassandra.stress.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsistencyLevelTest
{
    @Test
    public void mapsEveryLevelToTheSameNameInBothDrivers()
    {
        for (ConsistencyLevel level : ConsistencyLevel.values())
        {
            assertEquals(level.name(), level.ToV3Value().name());
            assertEquals(level.name(), level.ToV4Value().name());
        }
    }

    @Test
    public void marksSerialAndLocalLevels()
    {
        assertTrue(ConsistencyLevel.LOCAL_SERIAL.isSerialConsistency());
        assertFalse(ConsistencyLevel.QUORUM.isSerialConsistency());
        assertTrue(ConsistencyLevel.LOCAL_ONE.isDatacenterLocal());
        assertFalse(ConsistencyLevel.ONE.isDatacenterLocal());
    }
}
