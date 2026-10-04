package org.apache.cassandra.stress.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolVersionTest
{
    @ParameterizedTest
    @ValueSource(ints = { 3, 4, 5 })
    void mapsNumberedVersionsToBothDrivers(int version)
    {
        ProtocolVersion protocol = ProtocolVersion.fromInt(version);
        assertEquals(version, protocol.ToJavaDriverV3().toInt());
        assertEquals(version, protocol.ToJavaDriverV4().getCode());
        assertEquals(String.valueOf(version), protocol.toString());
    }

    @Test
    void defaultLetsDriver4Negotiate()
    {
        assertEquals(com.datastax.driver.core.ProtocolVersion.DEFAULT, ProtocolVersion.DEFAULT.ToJavaDriverV3());
        assertNull(ProtocolVersion.DEFAULT.ToJavaDriverV4());
        assertEquals("DEFAULT", ProtocolVersion.DEFAULT.toString());
    }

    @Test
    void newestSupportedIsV5()
    {
        assertEquals(com.datastax.driver.core.ProtocolVersion.V5, ProtocolVersion.NEWEST_SUPPORTED.ToJavaDriverV3());
        assertEquals(com.datastax.oss.driver.api.core.ProtocolVersion.V5, ProtocolVersion.NEWEST_SUPPORTED.ToJavaDriverV4());
        assertEquals("NEWEST_SUPPORTED", ProtocolVersion.NEWEST_SUPPORTED.toString());
    }

    @Test
    void rejectsInvalidVersions()
    {
        assertThrows(IllegalArgumentException.class, () -> ProtocolVersion.fromInt(0).ToJavaDriverV3());
        assertThrows(IllegalArgumentException.class, () -> ProtocolVersion.fromInt(9).ToJavaDriverV4());
        assertEquals("unknown version: -7", ProtocolVersion.fromInt(-7).toString());
    }
}
