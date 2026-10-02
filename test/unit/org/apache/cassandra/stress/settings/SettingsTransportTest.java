package org.apache.cassandra.stress.settings;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import org.apache.cassandra.stress.util.EncryptionOptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsTransportTest
{
    private static EncryptionOptions parse(String... params)
    {
        Map<String, String[]> clArgs = new HashMap<>();
        clArgs.put("-transport", params);
        return SettingsTransport.get(clArgs).getEncryptionOptions();
    }

    @Test
    public void encryptionIsOffWithoutTruststore()
    {
        assertFalse(parse("store-type=PKCS12").enabled);
    }

    @Test
    public void appliesTheStoreType()
    {
        EncryptionOptions options = parse("truststore=/tmp/ts.p12", "truststore-password=secret", "store-type=PKCS12");
        assertTrue(options.enabled);
        assertEquals("PKCS12", options.store_type);
    }

    @Test
    public void defaultsToJks()
    {
        assertEquals("JKS", parse("truststore=/tmp/ts.jks", "truststore-password=secret").store_type);
    }
}
