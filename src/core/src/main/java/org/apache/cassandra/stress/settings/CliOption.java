// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum CliOption {
    POP("Population distribution and intra-partition visit order", SettingsPopulation.helpPrinter()),
    INSERT(
            "Insert specific options relating to various methods for batching and splitting partition updates",
            SettingsInsert.helpPrinter()),
    COL(
            "Column details such as size and count distribution, data generator, names, comparator and if super"
                    + " columns should be used",
            SettingsColumn.helpPrinter()),
    RATE("Thread count, rate limit or automatic mode (default is auto)", SettingsRate.helpPrinter()),
    MODE("CQL driver and connection options", SettingsMode.helpPrinter()),
    ERRORS("How to handle errors when encountered during stress", SettingsErrors.helpPrinter()),
    SCHEMA("Replication settings, compression, compaction, storage etc.", SettingsSchema.helpPrinter()),
    NODE("Nodes to connect to", SettingsNode.helpPrinter()),
    LOG("Where to log progress to, and the interval at which to do it", SettingsLog.helpPrinter()),
    TRANSPORT("Custom transport factories", SettingsTransport.helpPrinter()),
    PORT("The port to connect to cassandra nodes on", SettingsPort.helpPrinter()),
    SENDTO("-send-to", "Specify a stress server to send this command to", SettingsMisc.sendToDaemonHelpPrinter()),
    GRAPH("-graph", "Graph recorded metrics", SettingsGraph.helpPrinter()),
    TOKENRANGE("Token range settings", SettingsTokenRange.helpPrinter());

    private static final Map<String, CliOption> LOOKUP;

    static {
        final Map<String, CliOption> lookup = new HashMap<>();
        for (CliOption cmd : values()) {
            lookup.put("-" + cmd.toString().toLowerCase(Locale.ROOT), cmd);
            if (cmd.extraName != null) {
                lookup.put(cmd.extraName, cmd);
            }
        }
        LOOKUP = lookup;
    }

    public static CliOption get(String command) {
        return LOOKUP.get(command.toLowerCase(Locale.ROOT));
    }

    public final String extraName;
    public final String description;
    private final Runnable helpPrinter;

    CliOption(String description, Runnable helpPrinter) {
        this(null, description, helpPrinter);
    }

    CliOption(String extraName, String description, Runnable helpPrinter) {
        this.extraName = extraName;
        this.description = description;
        this.helpPrinter = helpPrinter;
    }

    public void printHelp() {
        helpPrinter.run();
    }
}
