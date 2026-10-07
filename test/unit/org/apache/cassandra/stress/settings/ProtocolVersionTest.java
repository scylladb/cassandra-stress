package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolVersionTest {
    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void keepsTheNumberedVersion(int version) {
        ProtocolVersion protocol = ProtocolVersion.fromInt(version);
        assertFalse(protocol.isDefault());
        assertEquals(version, protocol.number());
        assertEquals(String.valueOf(version), protocol.toString());
    }

    @Test
    void defaultLetsTheDriverNegotiate() {
        assertTrue(ProtocolVersion.DEFAULT.isDefault());
        assertEquals("DEFAULT", ProtocolVersion.DEFAULT.toString());
    }

    @Test
    void newestSupportedIsV5() {
        assertEquals(5, ProtocolVersion.NEWEST_SUPPORTED.number());
        assertEquals("NEWEST_SUPPORTED", ProtocolVersion.NEWEST_SUPPORTED.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -7})
    void rejectsAnInvalidNumber(int version) {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtocolVersion.fromInt(version).number());
    }

    @Test
    void printsAnUnknownSpecialVersion() {
        assertEquals("unknown version: -7", ProtocolVersion.fromInt(-7).toString());
    }
}
