package org.apache.cassandra.stress.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SSLFactoryTest {
    @TempDir
    Path dir;

    @Test
    void loadsATruststoreWithoutAPassword() throws Exception {
        Path truststore = dir.resolve("truststore.jks");
        KeyStore store = KeyStore.getInstance("JKS");
        store.load(null, null);
        try (OutputStream out = Files.newOutputStream(truststore)) {
            store.store(out, "changeit".toCharArray());
        }
        EncryptionOptions options = new EncryptionOptions();
        options.truststore = truststore.toString();
        options.truststorePassword = null;
        options.keystore = truststore.toString();
        options.keystorePassword = null;

        assertNotNull(SSLFactory.createSSLContext(options, true));
    }
}
