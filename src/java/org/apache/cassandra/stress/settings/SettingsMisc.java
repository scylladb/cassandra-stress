// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.cassandra.stress.StressServer;
import org.apache.cassandra.stress.driver.StressClients;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.util.HostAndPort;

final class SettingsMisc {
    private SettingsMisc() {}

    static boolean maybeDoSpecial(Map<String, String[]> clArgs) {
        if (maybePrintHelp(clArgs)) return true;
        if (maybePrintDistribution(clArgs)) return true;
        if (maybePrintVersion(clArgs)) return true;
        return false;
    }

    private static final class PrintDistribution extends GroupedOptions {
        final OptionDistribution dist = new OptionDistribution("dist=", null, "A mathematical distribution");

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(dist);
        }
    }

    private static boolean maybePrintDistribution(Map<String, String[]> clArgs) {
        final String[] args = clArgs.get("print");
        if (args == null) return false;
        final PrintDistribution dist = new PrintDistribution();
        if (null == GroupedOptions.select(args, dist)) {
            throw new InvalidSettingsException(
                    "Invalid print options provided, see output for valid options", printHelpPrinter());
        }
        printDistribution(dist.dist.get().get());
        return true;
    }

    private static void printDistribution(Distribution dist) {
        System.out.printf("%% of samples    Range       %% of total%n");

        double rangemax = dist.inverseCumProb(1d) / 100d;
        for (double d : new double[] {0.1d, 0.2d, 0.3d, 0.4d, 0.5d, 0.6d, 0.7d, 0.8d, 0.9d, 0.95d, 0.99d, 1d}) {
            double sampleperc = d * 100;
            long max = dist.inverseCumProb(d);
            double rangeperc = max / rangemax;
            System.out.println(String.format("%-16.1f%-12d%12.1f", sampleperc, max, rangeperc));
        }
    }

    private static boolean maybePrintHelp(Map<String, String[]> clArgs) {
        if (!clArgs.containsKey("-?") && !clArgs.containsKey("help")) return false;
        String[] params = clArgs.remove("-?");
        if (params == null) params = clArgs.remove("help");
        if (params.length == 0) {
            if (!clArgs.isEmpty()) {
                if (clArgs.size() == 1) {
                    Map.Entry<String, String[]> only =
                            clArgs.entrySet().iterator().next();
                    if (only.getValue().length == 0) params = new String[] {only.getKey()};
                }
            } else {
                printHelp();
                return true;
            }
        }
        if (params.length == 1) {
            printHelp(params[0]);
            return true;
        }
        throw new IllegalArgumentException("Invalid command/option provided to help");
    }

    private static boolean maybePrintVersion(Map<String, String[]> clArgs) {
        if (clArgs.containsKey("version")) {
            System.out.println(
                    versionLines(stressVersion(), StressClients.driver3Version(), StressClients.driver4Version())
                            .trim());
            return true;
        }
        return false;
    }

    static String versionLines(String stressVersion, String driver3Version, String driver4Version) {
        return "Version: " + stressVersion + "\n"
                + "scylla-java-driver: " + driver3Version + "\n"
                + "scylla-java-driver-4x: " + driver4Version + "\n";
    }

    static String stressVersion() {
        try (InputStream in = SettingsMisc.class.getResourceAsStream("/org/apache/cassandra/stress/stress.version")) {
            if (in == null) return "unknown";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void printHelp() {
        System.out.println("Usage:      cassandra-stress <command> [options]");
        System.out.println("Help usage: cassandra-stress help <command>");
        System.out.println();
        System.out.println("---Commands---");
        for (Command cmd : Command.values()) {
            System.out.println(String.format("%-20s : %s", cmd.toString().toLowerCase(Locale.ROOT), cmd.description));
        }
        System.out.println();
        System.out.println("---Options---");
        for (CliOption cmd : CliOption.values()) {
            System.out.println(String.format("-%-20s : %s", cmd.toString().toLowerCase(Locale.ROOT), cmd.description));
        }
    }

    public static void printHelp(String command) {
        Command cmd = Command.get(command);
        if (cmd != null) {
            cmd.printHelp();
            return;
        }
        CliOption opt = CliOption.get(command);
        if (opt != null) {
            opt.printHelp();
            return;
        }
        printHelp();
        throw new IllegalArgumentException("Invalid command or option provided to command help");
    }

    static Runnable helpHelpPrinter() {
        return () -> {
            System.out.println("Usage: cassandra-stress help <command|option>");
            System.out.println("Commands:");
            for (Command cmd : Command.values())
                System.out.println("    " + cmd.names.toString().replaceAll("\\[|\\]", ""));
            System.out.println("Options:");
            for (CliOption op : CliOption.values())
                System.out.println("    -" + op.toString().toLowerCase(Locale.ROOT)
                        + (op.extraName != null ? ", " + op.extraName : ""));
        };
    }

    static Runnable printHelpPrinter() {
        return () -> GroupedOptions.printOptions(System.out, "print", new GroupedOptions() {
            @Override
            public List<? extends Option> options() {
                return Arrays.asList(new OptionDistribution("dist=", null, "A mathematical distribution"));
            }
        });
    }

    static Runnable sendToDaemonHelpPrinter() {
        return () -> {
            System.out.println("Usage: -sendto <host>[:<port>]");
            System.out.println();
            System.out.println("Specify a host running the stress server to send this stress command to.");
            System.out.println("The default port is " + StressServer.DEFAULT_PORT + ".");
        };
    }

    static String getSendToDaemon(Map<String, String[]> clArgs) {
        String[] params = clArgs.remove("-send-to");
        if (params == null) params = clArgs.remove("-sendto");
        if (params == null) return null;
        if (params.length != 1) {
            throw new InvalidSettingsException(
                    "Invalid -sendto specifier: " + Arrays.toString(params), sendToDaemonHelpPrinter());
        }
        try {
            HostAndPort.parse(params[0], StressServer.DEFAULT_PORT);
        } catch (IllegalArgumentException e) {
            throw new InvalidSettingsException(e.getMessage(), sendToDaemonHelpPrinter(), e);
        }
        return params[0];
    }
}
