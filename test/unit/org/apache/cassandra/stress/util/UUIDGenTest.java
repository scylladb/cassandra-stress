package org.apache.cassandra.stress.util;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UUIDGenTest
{
    @Test
    void timeUuidCarriesTheTimestamp()
    {
        UUID uuid = UUIDGen.getTimeUUID(1_700_000_000_123L, 0, 0x8000123456789abcL);
        assertEquals(1, uuid.version());
        assertEquals(0x8000123456789abcL, uuid.getLeastSignificantBits());
        assertEquals((1_700_000_000_123L + 12219292800000L) * 10_000, uuid.timestamp());
    }

    @Test
    void convertsToBytesAndBack()
    {
        UUID uuid = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
        assertEquals(uuid, UUIDGen.getUUID(UUIDGen.toByteBuffer(uuid)));
        assertEquals(16, UUIDGen.toByteBuffer(uuid).remaining());
    }
}
