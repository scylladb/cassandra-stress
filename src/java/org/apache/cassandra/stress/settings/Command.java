// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum Command {
    READ(
            false,
            "standard1",
            "Multiple concurrent reads - the cluster must first be populated by a write test",
            CommandCategory.BASIC),
    WRITE(true, "standard1", "insert", "Multiple concurrent writes against the cluster", CommandCategory.BASIC),
    MIXED(
            true,
            null,
            "Interleaving of any basic commands, with configurable ratio and distribution - the cluster must first be"
                    + " populated by a write test",
            CommandCategory.MIXED),
    COUNTER_WRITE(true, "counter1", "counter_add", "Multiple concurrent updates of counters.", CommandCategory.BASIC),
    COUNTER_READ(
            false,
            "counter1",
            "counter_get",
            "Multiple concurrent reads of counters. The cluster must first be populated by a counterwrite test.",
            CommandCategory.BASIC),
    USER(
            true,
            null,
            "Interleaving of user provided queries, with configurable ratio and distribution",
            CommandCategory.USER),

    HELP(false, null, "-?", "Print help for a command or option", null),
    PRINT(false, null, "Inspect the output of a distribution definition", null),
    VERSION(false, null, "Print the version of cassandra stress", null);

    private static final Map<String, Command> LOOKUP;

    static {
        final Map<String, Command> lookup = new HashMap<>();
        for (Command cmd : values()) {
            for (String name : cmd.names) {
                lookup.put(name, cmd);
            }
        }
        LOOKUP = lookup;
    }

    public static Command get(String command) {
        return LOOKUP.get(command.toLowerCase(Locale.ROOT));
    }

    public final boolean updates;
    public final CommandCategory category;
    public final List<String> names;
    public final String description;
    public final String table;

    Command(boolean updates, String table, String description, CommandCategory category) {
        this(updates, table, null, description, category);
    }

    Command(boolean updates, String table, String extra, String description, CommandCategory category) {
        this.table = table;
        this.updates = updates;
        this.category = category;
        List<String> names = new ArrayList<>();
        names.add(this.toString().toLowerCase(Locale.ROOT));
        names.add(this.toString().replaceAll("_", "").toLowerCase(Locale.ROOT));
        if (extra != null) {
            names.add(extra.toLowerCase(Locale.ROOT));
            names.add(extra.replaceAll("_", "").toLowerCase(Locale.ROOT));
        }
        this.names = List.copyOf(names);
        this.description = description;
    }

    public void printHelp() {
        helpPrinter().run();
    }

    public final Runnable helpPrinter() {
        if (this == PRINT) {
            return SettingsMisc.printHelpPrinter();
        }
        if (this == HELP) {
            return SettingsMisc.helpHelpPrinter();
        }
        if (category == null) {
            return () -> System.out.println("Usage: cassandra-stress " + names.getFirst() + "\n\n" + description);
        }
        return switch (category) {
            case USER -> SettingsCommandUser.helpPrinter();
            case BASIC -> SettingsCommandPreDefined.helpPrinter(this);
            case MIXED -> SettingsCommandPreDefinedMixed.helpPrinter();
        };
    }
}
