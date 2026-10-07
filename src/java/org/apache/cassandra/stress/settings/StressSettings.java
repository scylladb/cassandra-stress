// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressClients;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.CqlNames;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.apache.cassandra.stress.util.ResultLogger;

public class StressSettings {
    public final SettingsCommand command;
    public final SettingsRate rate;
    public final SettingsPopulation generate;
    public final SettingsInsert insert;
    public final SettingsColumn columns;
    public final SettingsErrors errors;
    public final SettingsLog log;
    public final SettingsMode mode;
    public final SettingsNode node;
    public final SettingsSchema schema;
    public final SettingsTransport transport;
    public final SettingsPort port;
    public final String sendToDaemon;
    public final SettingsGraph graph;
    public final SettingsTokenRange tokenRange;

    public StressSettings(
            SettingsCommand command,
            SettingsRate rate,
            SettingsPopulation generate,
            SettingsInsert insert,
            SettingsColumn columns,
            SettingsErrors errors,
            SettingsLog log,
            SettingsMode mode,
            SettingsNode node,
            SettingsSchema schema,
            SettingsTransport transport,
            SettingsPort port,
            String sendToDaemon,
            SettingsGraph graph,
            SettingsTokenRange tokenRange) {
        this.command = command;
        this.rate = rate;
        this.insert = insert;
        this.generate = generate;
        this.columns = columns;
        this.errors = errors;
        this.log = log;
        this.mode = mode;
        this.node = node;
        this.schema = schema;
        this.transport = transport;
        this.port = port;
        this.sendToDaemon = sendToDaemon;
        this.graph = graph;
        this.tokenRange = tokenRange;
    }

    private volatile ResultLogger output = new MultiResultLogger(System.out);
    private volatile StressClient client;
    private final Object clientLock = new Object();
    private int numFailures;
    private static int MAX_NUM_FAILURES = 10;

    public ResultLogger output() {
        return output;
    }

    public void setOutput(ResultLogger output) {
        this.output = Objects.requireNonNull(output);
    }

    public StressClient getClient() {
        return getClient(true);
    }

    public StressClient getClient(boolean setKeyspace) {
        if (client != null) {
            return client;
        }

        synchronized (clientLock) {
            if (numFailures >= MAX_NUM_FAILURES) {
                throw new RuntimeException("Failed to create client too many times");
            }

            if (client != null) {
                return client;
            }
            StressClient c = null;
            try {
                EncryptionOptions encOptions = transport.getEncryptionOptions();
                c = StressClients.create(this, node.nodes, port.nativePort, encOptions);
                c.connect(mode.compression());
                if (setKeyspace && schema.keyspace != null) {
                    c.execute("USE " + CqlNames.quote(schema.keyspace), ConsistencyLevel.ONE);
                }

                client = c;
                return c;
            } catch (Exception e) {
                numFailures += 1;
                if (c != null) {
                    try {
                        c.disconnect();
                    } catch (RuntimeException suppressed) {
                        e.addSuppressed(suppressed);
                    }
                }
                throw new RuntimeException(e);
            }
        }
    }

    public void maybeCreateKeyspaces() {
        if (command.type == Command.WRITE || command.type == Command.COUNTER_WRITE) {
            schema.createKeySpaces(this);
        } else if (command.type == Command.USER) {
            ((SettingsCommandUser) command).profiles.forEach((k, v) -> v.maybeCreateSchema(this));
        }
    }

    public static StressSettings parse(String[] args) {
        return parse(args, false);
    }

    public static StressSettings parseForDaemon(String[] args) {
        return parse(args, true);
    }

    private static StressSettings parse(String[] args, boolean daemon) {
        if (args.length == 0) {
            throw new InvalidSettingsException("No command provided", StressSettings::printHelp);
        }
        args = repairParams(args);
        final Map<String, String[]> clArgs = parseMap(args);
        if (daemon) {
            refuseDaemonFileAccess(clArgs);
        }
        if (clArgs.containsKey("legacy")) {
            throw new IllegalArgumentException(
                    "Command legacy was removed. Run cassandra-stress help to see the commands.");
        }
        if (SettingsMisc.maybeDoSpecial(clArgs)) {
            return null;
        }
        return get(clArgs);
    }

    private static void refuseDaemonFileAccess(Map<String, String[]> clArgs) {
        if (Command.USER.names.stream().anyMatch(clArgs::containsKey)) {
            throw new IllegalArgumentException("stressd runs the predefined commands only.");
        }
        if (hasValue(clArgs.get("-node"), "file=")) {
            throw new IllegalArgumentException("stressd refuses -node file=. Pass the nodes as a list.");
        }
        if (hasValue(clArgs.get("-log"), "hdrfile=")) {
            throw new IllegalArgumentException("stressd refuses -log hdrfile=.");
        }
    }

    private static boolean hasValue(String[] values, String prefix) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String[] repairParams(String[] args) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String arg : args) {
            if (!first) {
                sb.append(' ');
            }
            sb.append(arg);
            first = false;
        }
        return sb.toString()
                .replaceAll("\\s+([,=()])", "$1")
                .replaceAll("([,=(])\\s+", "$1")
                .split(" +");
    }

    public static StressSettings get(Map<String, String[]> clArgs) {
        SettingsCommand command = SettingsCommand.get(clArgs);
        if (command == null) {
            throw new IllegalArgumentException("No command specified");
        }
        String sendToDaemon = SettingsMisc.getSendToDaemon(clArgs);
        if (sendToDaemon != null && command.type == Command.USER) {
            throw new IllegalArgumentException(
                    "-send-to runs the predefined commands only. Run the user command without -send-to.");
        }
        SettingsPort port = SettingsPort.get(clArgs);
        SettingsRate rate = SettingsRate.get(clArgs, command);
        SettingsPopulation generate = SettingsPopulation.get(clArgs, command);
        SettingsTokenRange tokenRange = SettingsTokenRange.get(clArgs);
        SettingsInsert insert = SettingsInsert.get(clArgs);
        SettingsColumn columns = SettingsColumn.get(clArgs);
        SettingsErrors errors = SettingsErrors.get(clArgs);
        SettingsLog log = SettingsLog.get(clArgs);
        SettingsMode mode = SettingsMode.get(clArgs);
        SettingsNode node = SettingsNode.get(clArgs);
        SettingsSchema schema = SettingsSchema.get(clArgs, command);
        SettingsTransport transport = SettingsTransport.get(clArgs);
        SettingsGraph graph = SettingsGraph.get(clArgs, command);
        if (!clArgs.isEmpty()) {
            graph.deleteTemporaryLogFile();
            StringBuilder message =
                    new StringBuilder("Error processing command line arguments. The following were ignored:");
            for (Map.Entry<String, String[]> e : clArgs.entrySet()) {
                message.append(System.lineSeparator()).append(e.getKey());
                for (String v : e.getValue()) {
                    message.append(' ').append(v);
                }
            }
            throw new InvalidSettingsException(message.toString(), StressSettings::printHelp);
        }

        return new StressSettings(
                command,
                rate,
                generate,
                insert,
                columns,
                errors,
                log,
                mode,
                node,
                schema,
                transport,
                port,
                sendToDaemon,
                graph,
                tokenRange);
    }

    private static Map<String, String[]> parseMap(String[] args) {
        final LinkedHashMap<String, String[]> r = new LinkedHashMap<>();
        String key = null;
        List<String> params = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (i == 0 || args[i].startsWith("-")) {
                if (i > 0) {
                    putParam(key, params.toArray(new String[0]), r);
                }
                key = args[i].toLowerCase(Locale.ROOT);
                params.clear();
            } else {
                params.add(args[i]);
            }
        }
        putParam(key, params.toArray(new String[0]), r);
        return r;
    }

    private static void putParam(String key, String[] args, Map<String, String[]> clArgs) {
        String[] prev = clArgs.put(key, args);
        if (prev != null) {
            throw new IllegalArgumentException(
                    key + " is defined multiple times. Each option/command can be specified at most once.");
        }
    }

    public static void printHelp() {
        SettingsMisc.printHelp();
    }

    public void printSettings(ResultLogger out) {
        out.println("******************** Stress Settings ********************");
        out.println("Command:");
        command.printSettings(out);
        out.println("Rate:");
        rate.printSettings(out);
        out.println("Population:");
        generate.printSettings(out);
        out.println("Insert:");
        insert.printSettings(out);
        if (command.type != Command.USER) {
            out.println("Columns:");
            columns.printSettings(out);
        }
        out.println("Errors:");
        errors.printSettings(out);
        out.println("Log:");
        log.printSettings(out);
        out.println("Mode:");
        mode.printSettings(out);
        out.println("Node:");
        node.printSettings(out);
        out.println("Schema:");
        schema.printSettings(out);
        out.println("Transport:");
        transport.printSettings(out);
        out.println("Port:");
        port.printSettings(out);
        out.println("Send To Daemon:");
        out.printf("  " + (sendToDaemon != null ? sendToDaemon : "*not set*") + "%n");
        out.println("Graph:");
        graph.printSettings(out);
        out.println("TokenRange:");
        tokenRange.printSettings(out);

        if (command.type == Command.USER) {
            out.println();
            out.println("******************** Profile ********************");
            out.println("******************** Profile(s) ********************");
            ((SettingsCommandUser) command).profiles.forEach((k, v) -> v.printSettings(out, this));
        }

        out.println();
    }

    public void disconnect() {
        synchronized (clientLock) {
            if (client != null) {
                client.disconnect();
                client = null;
            }
        }
    }
}
