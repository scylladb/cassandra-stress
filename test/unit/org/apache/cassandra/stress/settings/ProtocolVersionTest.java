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
    void mapsNumberedVersionsToTheDriver(int version) {
        ProtocolVersion protocol = ProtocolVersion.fromInt(version);
        assertEquals(version, protocol.toDriver().getCode());
        assertEquals(String.valueOf(version), protocol.toString());
    }

    @Test
    void defaultLetsTheDriverNegotiate() {
        assertNull(ProtocolVersion.DEFAULT.toDriver());
        assertEquals("DEFAULT", ProtocolVersion.DEFAULT.toString());
    }

    @Test
    void newestSupportedIsV5() {
        assertEquals(com.datastax.oss.driver.api.core.ProtocolVersion.V5, ProtocolVersion.NEWEST_SUPPORTED.toDriver());
        assertEquals("NEWEST_SUPPORTED", ProtocolVersion.NEWEST_SUPPORTED.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 9})
    void rejectsVersionsTheDriverDoesNotSupport(int version) {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtocolVersion.fromInt(version).toDriver());
    }

    @Test
    void printsAnUnknownSpecialVersion() {
        assertEquals("unknown version: -7", ProtocolVersion.fromInt(-7).toString());
    }
}
