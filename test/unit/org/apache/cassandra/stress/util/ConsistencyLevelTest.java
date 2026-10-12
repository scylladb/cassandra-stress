package org.apache.cassandra.stress.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConsistencyLevelTest {
    @Test
    void marksSerialAndLocalLevels() {
        assertTrue(ConsistencyLevel.LOCAL_SERIAL.isSerialConsistency());
        assertFalse(ConsistencyLevel.QUORUM.isSerialConsistency());
        assertTrue(ConsistencyLevel.LOCAL_ONE.isDatacenterLocal());
        assertFalse(ConsistencyLevel.ONE.isDatacenterLocal());
    }
}
