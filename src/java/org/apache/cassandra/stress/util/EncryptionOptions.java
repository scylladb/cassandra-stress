// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import javax.net.ssl.SSLSocketFactory;

public class EncryptionOptions {
    public boolean enabled;
    public String keystore = "conf/.keystore";
    public String keystorePassword = "cassandra";
    public String truststore = "conf/.truststore";
    public String truststorePassword = "cassandra";
    public String[] cipherSuites = ((SSLSocketFactory) SSLSocketFactory.getDefault()).getDefaultCipherSuites();
    public String protocol = "TLS";
    public String algorithm = "SunX509";
    public String storeType = "JKS";
    public boolean hostnameVerification;
}
