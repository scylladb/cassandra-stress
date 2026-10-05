// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SSLFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLFactory.class);
    private static volatile boolean checkedExpiry;

    private SSLFactory() {}

    public static SSLContext createSSLContext(EncryptionOptions options, boolean buildTruststore) throws IOException {
        try {
            SSLContext ctx = SSLContext.getInstance(options.protocol);
            TrustManager[] trustManagers = null;

            if (buildTruststore) {
                try (InputStream tsf = Files.newInputStream(Paths.get(options.truststore))) {
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(options.algorithm);
                    KeyStore ts = KeyStore.getInstance(options.storeType);
                    ts.load(tsf, options.truststorePassword.toCharArray());
                    tmf.init(ts);
                    trustManagers = tmf.getTrustManagers();
                }
            }

            try (InputStream ksf = Files.newInputStream(Paths.get(options.keystore))) {
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(options.algorithm);
                KeyStore ks = KeyStore.getInstance(options.storeType);
                ks.load(ksf, options.keystorePassword.toCharArray());
                if (!checkedExpiry) {
                    for (Enumeration<String> aliases = ks.aliases(); aliases.hasMoreElements(); ) {
                        String alias = aliases.nextElement();
                        if ("X.509".equals(ks.getCertificate(alias).getType())) {
                            Date expires = ((X509Certificate) ks.getCertificate(alias)).getNotAfter();
                            if (expires.before(new Date()))
                                LOGGER.warn("Certificate for {} expired on {}", alias, expires);
                        }
                    }
                    checkedExpiry = true;
                }
                kmf.init(ks, options.keystorePassword.toCharArray());
                ctx.init(kmf.getKeyManagers(), trustManagers, null);
            }
            return ctx;
        } catch (Exception e) {
            throw new IOException("Error creating the initializing the SSL Context", e);
        }
    }
}
