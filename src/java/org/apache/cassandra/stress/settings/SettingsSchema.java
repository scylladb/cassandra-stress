// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.*;

import com.datastax.driver.core.exceptions.AlreadyExistsException;
import org.apache.cassandra.stress.util.JavaDriverClient;
import org.apache.cassandra.stress.util.JavaDriverV4Client;
import org.apache.cassandra.stress.util.QueryExecutor;
import org.apache.cassandra.stress.util.ResultLogger;
import org.apache.cassandra.stress.util.ByteBufferUtil;
import org.apache.cassandra.stress.StressProfile;

public class SettingsSchema
{
    private final String replicationStrategy;
    private final Map<String, String> replicationStrategyOptions;

    private final String storage;
    private final Map<String, String> storageOptions;

    private final String compression;
    private final String compactionStrategy;
    private final Map<String, String> compactionStrategyOptions;
    public final String keyspace;
    private final Command cmd_type;

    public SettingsSchema(Options options, SettingsCommand command) {
        keyspace = switch (command) {
            case SettingsCommandUser cmd -> null;
            default -> options.keyspace.value();
        };

        replicationStrategy = options.replication.getStrategy();
        replicationStrategyOptions = options.replication.getOptions();
        storage = options.storage.getType();
        storageOptions = options.storage.getOptions();
        compression = options.compression.value();
        compactionStrategy = options.compaction.getStrategy();
        compactionStrategyOptions = options.compaction.getOptions();
        cmd_type = command.type;
    }

    public void createKeySpaces(StressSettings settings)
    {
        createKeySpacesNative(settings);
    }

    public void createKeySpacesNative(StressSettings settings)
    {

        QueryExecutor client;
        if (settings.mode.api == ConnectionAPI.JAVA_DRIVER4_NATIVE) {
            client = (QueryExecutor) settings.getJavaDriverV4Client(false);
        } else {
            client = (QueryExecutor) settings.getJavaDriverClient(false);
        }

        try
        {
            client.execute(createKeyspaceStatementCQL3(), org.apache.cassandra.stress.util.ConsistencyLevel.LOCAL_QUORUM);

            client.execute("USE \""+keyspace+"\"", org.apache.cassandra.stress.util.ConsistencyLevel.LOCAL_QUORUM);

            client.execute(createStandard1StatementCQL3(settings), org.apache.cassandra.stress.util.ConsistencyLevel.LOCAL_QUORUM);

            if (cmd_type == Command.COUNTER_WRITE)
            {
                client.execute(createCounter1StatementCQL3(settings), org.apache.cassandra.stress.util.ConsistencyLevel.LOCAL_QUORUM);
            }

            System.out.println(String.format("Created keyspaces. Sleeping %ss for propagation.", settings.node.nodes.size()));
            Thread.sleep(settings.node.nodes.size() * 1000L);
        }
        catch (AlreadyExistsException | com.datastax.oss.driver.api.core.servererrors.AlreadyExistsException e)
        {
        }
        catch (Exception e)
        {
            throw new RuntimeException("Encountered exception creating schema", e);
        }
    }

    String createKeyspaceStatementCQL3()
    {
        StringBuilder b = new StringBuilder();

        b.append("CREATE KEYSPACE IF NOT EXISTS \"")
                .append(keyspace)
                .append("\" WITH replication = {'class': '")
                .append(replicationStrategy)
                .append("'");

        if (replicationStrategyOptions.isEmpty()) {
            b.append(", 'replication_factor': '1'}");
        } else {
            for (Map.Entry<String, String> entry : replicationStrategyOptions.entrySet()) {
                b.append(", '").append(entry.getKey()).append("' : '").append(entry.getValue()).append("'");
            }

            b.append("}");
        }

        if (storage != null) {
            b.append(" AND storage = {");
            b.append("'type': '").append(storage).append("'");
            for (Map.Entry<String, String> entry : storageOptions.entrySet()) {
                b.append(", '").append(entry.getKey()).append("' : '").append(entry.getValue()).append("'");
            }
            b.append("}");
        }

        b.append(" AND durable_writes = true;\n");

        return b.toString();
    }

    String createStandard1StatementCQL3(StressSettings settings) {

        StringBuilder b = new StringBuilder();

        b.append("CREATE TABLE IF NOT EXISTS ")
                .append("standard1 (key blob PRIMARY KEY ");

        try {
            for (ByteBuffer name : settings.columns.names)
                b.append("\n, \"").append(ByteBufferUtil.string(name)).append("\" blob");
        } catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        }

        b.append(") WITH compression = {");
        if (compression != null)
            b.append("'sstable_compression' : '").append(compression).append("'");

        b.append("}");

        if (compactionStrategy != null) {
            b.append(" AND compaction = { 'class' : '").append(compactionStrategy).append("'");

            for (Map.Entry<String, String> entry : compactionStrategyOptions.entrySet())
                b.append(", '").append(entry.getKey()).append("' : '").append(entry.getValue()).append("'");

            b.append("}");
        }

        b.append(";\n");

        return b.toString();
    }

    String createCounter1StatementCQL3(StressSettings settings) {

        StringBuilder b = new StringBuilder();

        b.append("CREATE TABLE IF NOT EXISTS ")
                .append("counter1 (key blob PRIMARY KEY,");

        try {
            for (ByteBuffer name : settings.columns.names)
                b.append("\n, \"").append(ByteBufferUtil.string(name)).append("\" counter");
        } catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        }

        b.append(") WITH compression = {");
        if (compression != null)
            b.append("'sstable_compression' : '").append(compression).append("'");

        b.append("}");

        if (compactionStrategy != null) {
            b.append(" AND compaction = { 'class' : '").append(compactionStrategy).append("'");

            for (Map.Entry<String, String> entry : compactionStrategyOptions.entrySet())
                b.append(", '").append(entry.getKey()).append("' : '").append(entry.getValue()).append("'");

            b.append("}");
        }

        b.append(";\n");

        return b.toString();
    }

    private static final class Options extends GroupedOptions {
        final OptionReplication replication = new OptionReplication();
        final OptionStorage storage = new OptionStorage();
        final OptionCompaction compaction = new OptionCompaction();
        final OptionSimple keyspace = new OptionSimple("keyspace=", ".*", "keyspace1", "The keyspace name to use", false);
        final OptionSimple compression = new OptionSimple("compression=", ".*", null, "Specify the compression to use for sstable, default:no compression", false);

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(replication, storage, keyspace, compaction, compression);
        }
    }

    public void printSettings(ResultLogger out) {
        out.println("  Keyspace: " + keyspace);
        out.println("  Replication Strategy: " + replicationStrategy);
        out.println("  Replication Strategy Options: " + replicationStrategyOptions);
        out.println("  Storage Options: " + storageOptions);

        out.println("  Table Compression: " + compression);
        out.println("  Table Compaction Strategy: " + compactionStrategy);
        out.println("  Table Compaction Strategy Options: " + compactionStrategyOptions);
    }

    public static SettingsSchema get(Map<String, String[]> clArgs, SettingsCommand command) {
        String[] params = clArgs.remove("-schema");
        if (params == null)
            return new SettingsSchema(new Options(), command);

        if (command instanceof SettingsCommandUser)
            throw new IllegalArgumentException("-schema can only be provided with predefined operations insert, read, etc.; the 'user' command requires a schema yaml instead");

        GroupedOptions options = GroupedOptions.select(params, new Options());
        if (options == null) {
            printHelp();
            System.out.println("Invalid -schema options provided, see output for valid options");
            System.exit(1);
        }
        return new SettingsSchema((Options) options, command);
    }

    public static void printHelp() {
        GroupedOptions.printOptions(System.out, "-schema", new Options());
    }

    public static Runnable helpPrinter() {
        return () -> printHelp();
    }

}
