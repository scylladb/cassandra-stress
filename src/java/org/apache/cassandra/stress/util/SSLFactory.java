// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.io.FileInputStream;
import java.io.IOException;
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

public final class SSLFactory
{
    private static final Logger logger = LoggerFactory.getLogger(SSLFactory.class);
    private static volatile boolean checkedExpiry = false;

    private SSLFactory()
    {
    }

    public static SSLContext createSSLContext(EncryptionOptions options, boolean buildTruststore) throws IOException
    {
        try
        {
            SSLContext ctx = SSLContext.getInstance(options.protocol);
            TrustManager[] trustManagers = null;

            if (buildTruststore)
            {
                try (FileInputStream tsf = new FileInputStream(options.truststore))
                {
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(options.algorithm);
                    KeyStore ts = KeyStore.getInstance(options.store_type);
                    ts.load(tsf, options.truststore_password.toCharArray());
                    tmf.init(ts);
                    trustManagers = tmf.getTrustManagers();
                }
            }

            try (FileInputStream ksf = new FileInputStream(options.keystore))
            {
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(options.algorithm);
                KeyStore ks = KeyStore.getInstance(options.store_type);
                ks.load(ksf, options.keystore_password.toCharArray());
                if (!checkedExpiry)
                {
                    for (Enumeration<String> aliases = ks.aliases(); aliases.hasMoreElements(); )
                    {
                        String alias = aliases.nextElement();
                        if (ks.getCertificate(alias).getType().equals("X.509"))
                        {
                            Date expires = ((X509Certificate) ks.getCertificate(alias)).getNotAfter();
                            if (expires.before(new Date()))
                                logger.warn("Certificate for {} expired on {}", alias, expires);
                        }
                    }
                    checkedExpiry = true;
                }
                kmf.init(ks, options.keystore_password.toCharArray());
                ctx.init(kmf.getKeyManagers(), trustManagers, null);
            }
            return ctx;
        }
        catch (Exception e)
        {
            throw new IOException("Error creating the initializing the SSL Context", e);
        }
    }
}
