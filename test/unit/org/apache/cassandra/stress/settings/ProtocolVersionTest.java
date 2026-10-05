package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolVersionTest {
    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void mapsNumberedVersionsToBothDrivers(int version) {
        ProtocolVersion protocol = ProtocolVersion.fromInt(version);
        assertEquals(version, protocol.toJavaDriverV3().toInt());
        assertEquals(version, protocol.toJavaDriverV4().getCode());
        assertEquals(String.valueOf(version), protocol.toString());
    }

    @Test
    void defaultLetsDriver4Negotiate() {
        assertEquals(com.datastax.driver.core.ProtocolVersion.DEFAULT, ProtocolVersion.DEFAULT.toJavaDriverV3());
        assertNull(ProtocolVersion.DEFAULT.toJavaDriverV4());
        assertEquals("DEFAULT", ProtocolVersion.DEFAULT.toString());
    }

    @Test
    void newestSupportedIsV5() {
        assertEquals(com.datastax.driver.core.ProtocolVersion.V5, ProtocolVersion.NEWEST_SUPPORTED.toJavaDriverV3());
        assertEquals(
                com.datastax.oss.driver.api.core.ProtocolVersion.V5, ProtocolVersion.NEWEST_SUPPORTED.toJavaDriverV4());
        assertEquals("NEWEST_SUPPORTED", ProtocolVersion.NEWEST_SUPPORTED.toString());
    }

    @Test
    void rejectsInvalidVersions() {
        assertThrows(
                IllegalArgumentException.class, () -> ProtocolVersion.fromInt(0).toJavaDriverV3());
        assertThrows(
                IllegalArgumentException.class, () -> ProtocolVersion.fromInt(9).toJavaDriverV4());
        assertEquals("unknown version: -7", ProtocolVersion.fromInt(-7).toString());
    }
}
