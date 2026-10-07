// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.cassandra.stress.util.ResultLogger;

public class SettingsNode {
    public final List<String> nodes;
    public final boolean isWhiteList;
    public final String datacenter;
    public final String rack;
    public final LoadBalanceType loadBalance;
    public final Integer usedHostsPerRemoteDc;

    public SettingsNode(Options options) {
        if (options.file.setByUser()) {
            try {
                String node;
                List<String> tmpNodes = new ArrayList<>();
                try (BufferedReader in = Files.newBufferedReader(Paths.get(options.file.value()))) {
                    while ((node = in.readLine()) != null) {
                        if (node.length() > 0) {
                            tmpNodes.add(node);
                        }
                    }
                    nodes = Arrays.asList(tmpNodes.toArray(new String[0]));
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }

        } else {
            nodes = Arrays.asList(options.list.value().split(","));
        }

        isWhiteList = options.whitelist.setByUser();
        datacenter = options.datacenter.value();
        rack = options.rack.value();
        loadBalance = LoadBalanceType.fromString(options.loadBalance.value());

        if (options.usedHostsPerRemoteDc.setByUser()) {
            try {
                int value = Integer.parseInt(options.usedHostsPerRemoteDc.value());
                if (value <= 0) {
                    throw new IllegalArgumentException("remote-dc must be a positive integer greater than zero");
                }
                usedHostsPerRemoteDc = value;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "remote-dc must be a valid integer: " + options.usedHostsPerRemoteDc.value(), e);
            }
        } else {
            usedHostsPerRemoteDc = null;
        }
    }

    public static final class Options extends GroupedOptions {
        final OptionSimple datacenter = new OptionSimple(
                "datacenter=", ".*", null, "Local datacenter for dc-aware and rack-aware load balancing", false);
        final OptionSimple rack =
                new OptionSimple("rack=", ".*", null, "Local rack for rack-aware load balancing", false);
        final OptionSimple whitelist =
                new OptionSimple("whitelist", "", null, "Limit communications to the provided nodes", false);
        final OptionSimple file = new OptionSimple("file=", ".*", null, "Node file (one per line)", false);
        final OptionSimple list =
                new OptionSimple("", "[^=,]+(,[^=,]+)*", "localhost", "comma delimited list of nodes", false);
        final OptionSimple loadBalance = new OptionSimple(
                "loadbalance=", ".*", null, "Load balancing strategy: round-robin, dc-aware, or rack-aware", false);
        final OptionSimple usedHostsPerRemoteDc = new OptionSimple(
                "remote-dc=",
                "[1-9][0-9]*",
                null,
                "Number of hosts from remote DCs to use for failover (used with dc-aware load balancing)",
                false);

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(datacenter, rack, whitelist, file, loadBalance, usedHostsPerRemoteDc, list);
        }
    }

    public void printSettings(ResultLogger out) {
        out.println("  Nodes: " + nodes);
        out.println("  Is White List: " + isWhiteList);
        out.println("  Datacenter: " + datacenter);
        out.println("  Rack: " + rack);
        out.println("  Load Balance: " + (loadBalance != null ? loadBalance.toString() : "auto"));
        out.println("  Remote DC Hosts: " + (usedHostsPerRemoteDc != null ? usedHostsPerRemoteDc : "disabled"));
    }

    public static SettingsNode get(Map<String, String[]> clArgs) {
        String[] params = clArgs.remove("-node");
        if (params == null) {
            return new SettingsNode(new Options());
        }

        GroupedOptions options = GroupedOptions.select(params, new Options());
        if (options == null) {
            throw new InvalidSettingsException(
                    "Invalid -node options provided, see output for valid options", SettingsNode::printHelp);
        }
        return new SettingsNode((Options) options);
    }

    public static void printHelp() {
        GroupedOptions.printOptions(System.out, "-node", new Options());
    }

    public static Runnable helpPrinter() {
        return SettingsNode::printHelp;
    }
}
