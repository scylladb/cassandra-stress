// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.cassandra.stress.util.ResultLogger;

public class SettingsMode {

    public final ConnectionAPI api;
    public final ConnectionStyle style;
    public final CqlVersion cqlVersion;
    public final ProtocolVersion protocolVersion;

    public final String username;
    public final String password;
    public final AuthProvider authProvider;

    public final Integer maxPendingPerConnection;
    public final Integer connectionsPerHost;
    public final Integer requestTimeout;

    private final ProtocolCompression compression;

    public SettingsMode(GroupedOptions options) {
        if (options instanceof Options opts) {
            api = "4x".equals(opts.driver.value())
                    ? ConnectionAPI.JAVA_DRIVER4_NATIVE
                    : ConnectionAPI.JAVA_DRIVER_NATIVE;
            cqlVersion = CqlVersion.CQL3;
            if ("NEWEST_SUPPORTED".equals(opts.protocolVersion.value())) {
                protocolVersion = ProtocolVersion.NEWEST_SUPPORTED;
            } else if ("DEFAULT".equals(opts.protocolVersion.value())) {
                protocolVersion = ProtocolVersion.DEFAULT;
            } else {
                protocolVersion = ProtocolVersion.fromInt(Integer.parseInt(opts.protocolVersion.value()));
            }
            style = opts.useUnPrepared.setByUser() ? ConnectionStyle.CQL : ConnectionStyle.CQL_PREPARED;
            compression =
                    ProtocolCompression.valueOf(opts.useCompression.value().toUpperCase(Locale.ROOT));
            username = opts.user.value();
            password = opts.password.value();
            maxPendingPerConnection = opts.maxPendingPerConnection.value().isEmpty()
                    ? null
                    : Integer.valueOf(opts.maxPendingPerConnection.value());
            connectionsPerHost =
                    opts.connectionsPerHost.value().isEmpty() ? null : Integer.valueOf(opts.connectionsPerHost.value());
            if (opts.requestTimeout.value().isEmpty()) {
                requestTimeout = null;
            } else {
                try {
                    requestTimeout = Integer.valueOf(opts.requestTimeout.value());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid value for requestTimeout: " + opts.requestTimeout.value(), e);
                }
            }
            authProvider = new AuthProvider(opts.authProvider.value());
        } else {
            throw new IllegalStateException();
        }
    }

    public ProtocolCompression compression() {
        return compression;
    }

    private static final class Options extends GroupedOptions {
        final OptionSimple api = new OptionSimple("cql3", "", null, "", true);
        final OptionSimple driver =
                new OptionSimple("", "native|4x", null, "native: the Java driver 3.x, 4x: the Java driver 4.x", false);
        final OptionSimple protocolVersion =
                new OptionSimple("protocolVersion=", "[3-5]", "DEFAULT", "CQL Protocol Version", false);
        final OptionSimple useUnPrepared =
                new OptionSimple("unprepared", "", null, "force use of unprepared statements", false);
        final OptionSimple useCompression = new OptionSimple("compression=", "none|lz4|snappy", "none", "", false);
        final OptionSimple port = new OptionSimple("port=", "[0-9]+", "9046", "", false);
        final OptionSimple user = new OptionSimple("user=", ".+", null, "username", false);
        final OptionSimple password = new OptionSimple("password=", ".+", null, "password", false);
        final OptionSimple authProvider =
                new OptionSimple("auth-provider=", ".*", null, "Authentication provider: PlainTextAuthProvider", false);
        final OptionSimple maxPendingPerConnection =
                new OptionSimple("maxPending=", "[0-9]+", "", "Maximum pending requests per connection", false);
        final OptionSimple connectionsPerHost =
                new OptionSimple("connectionsPerHost=", "[0-9]+", "8", "Number of connections per host", false);
        final OptionSimple requestTimeout =
                new OptionSimple("requestTimeout=", "[0-9]+", "12000", "Request timeout in milliseconds", false);

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(
                    useUnPrepared,
                    api,
                    useCompression,
                    port,
                    user,
                    password,
                    authProvider,
                    maxPendingPerConnection,
                    connectionsPerHost,
                    requestTimeout,
                    protocolVersion,
                    driver);
        }
    }

    public void printSettings(ResultLogger out) {
        out.printf("  API: %s%n", api);
        out.printf("  Connection Style: %s%n", style);
        out.printf("  CQL Version: %s%n", cqlVersion);
        out.printf("  Protocol Version: %s%n", protocolVersion);
        out.printf("  Username: %s%n", username);
        out.printf("  Password: %s%n", (password == null ? password : "*suppressed*"));
        out.printf("  Auth Provide Class: %s%n", authProvider == null ? "none" : authProvider.getClassName());
        out.printf("  Max Pending Per Connection: %d%n", maxPendingPerConnection);
        out.printf("  Connections Per Host: %d%n", connectionsPerHost);
        if (requestTimeout != null) {
            out.printf("  Request Timeout: %d ms%n", requestTimeout);
        }
        out.printf("  Compression: %s%n", compression);
    }

    public static SettingsMode get(Map<String, String[]> clArgs) {
        String[] params = clArgs.remove("-mode");
        if (params == null) {
            Options opts = new Options();
            opts.accept("cql3");
            return new SettingsMode(opts);
        }

        rejectRemovedModes(params);
        GroupedOptions options = GroupedOptions.select(params, new Options());
        if (options == null) {
            throw new InvalidSettingsException(
                    "Invalid -mode options provided, see output for valid options", SettingsMode::printHelp);
        }
        return new SettingsMode(options);
    }

    private static final List<String> REMOVED_MODES = List.of("simplenative");

    private static void rejectRemovedModes(String[] params) {
        for (String param : params) {
            if (REMOVED_MODES.contains(param)) {
                throw new IllegalArgumentException(
                        "Mode " + param + " was removed. Use -mode cql3 native or -mode cql3 4x.");
            }
        }
    }

    public static void printHelp() {
        GroupedOptions.printOptions(System.out, "-mode", new Options());
    }

    public static Runnable helpPrinter() {
        return () -> printHelp();
    }
}
