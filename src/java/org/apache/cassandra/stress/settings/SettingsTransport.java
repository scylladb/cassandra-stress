// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.cassandra.stress.util.EncryptionOptions;
import org.apache.cassandra.stress.util.ResultLogger;

public class SettingsTransport
{

    private final TOptions options;

    public SettingsTransport(TOptions options)
    {
        this.options = options;
    }

    public EncryptionOptions getEncryptionOptions()
    {
        EncryptionOptions encOptions = new EncryptionOptions();
        if (options.trustStore.present())
        {
            encOptions.enabled = true;
            encOptions.truststore = options.trustStore.value();
            encOptions.truststore_password = options.trustStorePw.value();
            if (options.keyStore.present())
            {
                encOptions.keystore = options.keyStore.value();
                encOptions.keystore_password = options.keyStorePw.value();
            }
            else
            {
                encOptions.keystore = encOptions.truststore;
                encOptions.keystore_password = encOptions.truststore_password;
            }
            encOptions.algorithm = options.alg.value();
            encOptions.store_type = options.storeType.value();
            encOptions.protocol = options.protocol.value();
            encOptions.cipher_suites = options.ciphers.value().split(",");
            encOptions.hostname_verification = Boolean.parseBoolean(options.hostnameVerification.value());
        }
        return encOptions;
    }

    static class TOptions extends GroupedOptions
    {
        final OptionSimple trustStore = new OptionSimple("truststore=", ".*", null, "SSL: full path to truststore", false);
        final OptionSimple trustStorePw = new OptionSimple("truststore-password=", ".*", null, "SSL: truststore password", false);
        final OptionSimple keyStore = new OptionSimple("keystore=", ".*", null, "SSL: full path to keystore", false);
        final OptionSimple keyStorePw = new OptionSimple("keystore-password=", ".*", null, "SSL: keystore password", false);
        final OptionSimple protocol = new OptionSimple("ssl-protocol=", ".*", "TLS", "SSL: connection protocol to use", false);
        final OptionSimple alg = new OptionSimple("ssl-alg=", ".*", "SunX509", "SSL: algorithm", false);
        final OptionSimple storeType = new OptionSimple("store-type=", ".*", "JKS", "SSL: keystore format", false);
        final OptionSimple ciphers = new OptionSimple("ssl-ciphers=", ".*", "TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "SSL: comma delimited list of encryption suites to use", false);
        final OptionSimple hostnameVerification = new OptionSimple("hostname-verification=", ".*", "false", "SSL: enable hostname verification in Java Driver", false);

        @Override
        public List<? extends Option> options()
        {
            return Arrays.asList(trustStore, trustStorePw, keyStore, keyStorePw, protocol, alg, storeType, ciphers, hostnameVerification);
        }
    }

    public void printSettings(ResultLogger out)
    {
        out.println("  " + options.getOptionAsString());
    }

    public static SettingsTransport get(Map<String, String[]> clArgs)
    {
        String[] params = clArgs.remove("-transport");
        if (params == null)
            return new SettingsTransport(new TOptions());

        GroupedOptions options = GroupedOptions.select(params, new TOptions());
        if (options == null)
        {
            printHelp();
            System.out.println("Invalid -transport options provided, see output for valid options");
            System.exit(1);
        }
        return new SettingsTransport((TOptions) options);
    }

    public static void printHelp()
    {
        GroupedOptions.printOptions(System.out, "-transport", new TOptions());
    }

    public static Runnable helpPrinter()
    {
        return () -> printHelp();
    }

}
