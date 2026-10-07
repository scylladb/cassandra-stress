// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.cassandra.stress.util.ResultLogger;

public class SettingsPort {

    public final int nativePort;

    public SettingsPort(PortOptions options) {
        nativePort = Integer.parseInt(options.nativePort.value());
    }

    private static final class PortOptions extends GroupedOptions {
        final OptionSimple nativePort =
                new OptionSimple("native=", "[0-9]+", "9042", "Use this port for the Cassandra native protocol", false);

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(nativePort);
        }
    }

    public void printSettings(ResultLogger out) {
        out.printf("  Native Port: %d%n", nativePort);
    }

    public static SettingsPort get(Map<String, String[]> clArgs) {
        String[] params = clArgs.remove("-port");
        if (params == null) {
            return new SettingsPort(new PortOptions());
        }
        rejectRemovedPorts(params);
        PortOptions options = GroupedOptions.select(params, new PortOptions());
        if (options == null) {
            throw new InvalidSettingsException(
                    "Invalid -port options provided, see output for valid options", SettingsPort::printHelp);
        }
        return new SettingsPort(options);
    }

    private static final List<String> REMOVED_PORTS = List.of("jmx=");

    private static void rejectRemovedPorts(String[] params) {
        for (String param : params) {
            for (String removed : REMOVED_PORTS) {
                if (param.startsWith(removed)) {
                    throw new IllegalArgumentException("Port option " + removed + " was removed. Use -port native=.");
                }
            }
        }
    }

    public static void printHelp() {
        GroupedOptions.printOptions(System.out, "-port", new PortOptions());
    }

    public static Runnable helpPrinter() {
        return () -> printHelp();
    }
}
