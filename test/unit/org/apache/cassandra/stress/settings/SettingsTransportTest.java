package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.junit.jupiter.api.Test;

class SettingsTransportTest {
    private static EncryptionOptions parse(String... params) {
        return SettingsTransport.get(new HashMap<>(Map.of("-transport", params)))
                .getEncryptionOptions();
    }

    @Test
    void encryptionIsOffWithoutTruststore() {
        assertFalse(parse("store-type=PKCS12").enabled);
    }

    @Test
    void appliesTheStoreType() {
        EncryptionOptions options = parse("truststore=/tmp/ts.p12", "truststore-password=secret", "store-type=PKCS12");
        assertTrue(options.enabled);
        assertEquals("PKCS12", options.storeType);
    }

    @Test
    void defaultsToJks() {
        assertEquals("JKS", parse("truststore=/tmp/ts.jks", "truststore-password=secret").storeType);
    }
}
