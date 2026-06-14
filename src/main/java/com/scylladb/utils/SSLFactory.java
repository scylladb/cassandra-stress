package com.scylladb.utils;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;

public class SSLFactory {

    public static SSLContext createSSLContext(EncryptionOptions.ClientEncryptionOptions options, boolean clientMode) throws Exception {
        SSLContext ctx = SSLContext.getInstance(options.protocol);

        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fs = new FileInputStream(options.keystore)) {
            ks.load(fs, options.keystore_password.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(options.algorithm);
        kmf.init(ks, options.keystore_password.toCharArray());

        KeyStore ts = KeyStore.getInstance("JKS");
        try (FileInputStream fs = new FileInputStream(options.truststore)) {
            ts.load(fs, options.truststore_password.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(options.algorithm);
        tmf.init(ts);

        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }
}
