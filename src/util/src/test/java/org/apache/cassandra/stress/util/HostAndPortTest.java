package org.apache.cassandra.stress.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HostAndPortTest {
    @ParameterizedTest
    @CsvSource({
        "10.0.0.1, 10.0.0.1, 2159",
        "10.0.0.1:3000, 10.0.0.1, 3000",
        "stress.local:3001, stress.local, 3001",
        "::1, ::1, 2159",
        "[::1], ::1, 2159",
        "[fe80::1]:3002, fe80::1, 3002",
    })
    void parsesTheHostAndTheOptionalPort(String address, String host, int port) {
        assertEquals(new HostAndPort(host, port), HostAndPort.parse(address, 2159));
    }

    @ParameterizedTest
    @ValueSource(strings = {"host:", "host:x", "host:0", "host:65536", ":3000", "[::1", "[::1]x", "[::1]:"})
    void rejectsAnInvalidAddress(String address) {
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parse(address, 2159));
    }

    @ParameterizedTest
    @CsvSource({"10.0.0.1, 2159, 10.0.0.1:2159", "::1, 3000, [::1]:3000"})
    void printsAnAddressThatParsesBack(String host, int port, String printed) {
        HostAndPort address = new HostAndPort(host, port);
        assertEquals(printed, address.toString());
        assertEquals(address, HostAndPort.parse(printed, 9042));
    }
}
