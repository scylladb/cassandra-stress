// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.io.Serializable;

import javax.net.ssl.SSLSocketFactory;

public class EncryptionOptions implements Serializable
{
    public boolean enabled = false;
    public String keystore = "conf/.keystore";
    public String keystore_password = "cassandra";
    public String truststore = "conf/.truststore";
    public String truststore_password = "cassandra";
    public String[] cipher_suites = ((SSLSocketFactory) SSLSocketFactory.getDefault()).getDefaultCipherSuites();
    public String protocol = "TLS";
    public String algorithm = "SunX509";
    public String store_type = "JKS";
    public boolean hostname_verification = false;
}
