package org.apache.cassandra.stress.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
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

    @Test
    void namesNoDetailOfAStoreThatDoesNotLoad() throws Exception {
        Path notAStore = Files.writeString(dir.resolve("hosts"), "127.0.0.1 localhost\n");
        Path missing = dir.resolve("missing.jks");

        for (Path path : List.of(notAStore, missing)) {
            EncryptionOptions options = new EncryptionOptions();
            options.truststore = path.toString();
            options.keystore = path.toString();

            IOException e = assertThrows(IOException.class, () -> SSLFactory.createSSLContext(options, true));

            assertEquals("Could not create the SSL context", e.getMessage());
        }
    }
}
