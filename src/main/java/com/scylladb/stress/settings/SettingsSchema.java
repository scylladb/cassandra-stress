/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package com.scylladb.stress.settings;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.*;

import com.datastax.driver.core.exceptions.AlreadyExistsException;
import com.scylladb.stress.util.QueryExecutor;
import com.scylladb.stress.util.ResultLogger;
import com.scylladb.utils.ByteBufferUtil;

public class SettingsSchema implements Serializable {

    public static final String DEFAULT_VALIDATOR = "BytesType";

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
            case SettingsCommandUser cmd ->
                    null; //this should never be used - StressProfile passes keyspace name directly
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

    public void createKeySpaces(StressSettings settings) {
        createKeySpacesNative(settings);
    }

    /**
     * Create Keyspace with Standard and Super/Counter column families
     */
    public void createKeySpacesNative(StressSettings settings) {

        QueryExecutor client;
        if (settings.mode().api == ConnectionAPI.JAVA_DRIVER4_NATIVE) {
            client = (QueryExecutor) settings.getJavaDriverV4Client(false);
        } else {
            client = (QueryExecutor) settings.getJavaDriverClient(false);
        }

        try {
            //Keyspace
            client.execute(createKeyspaceStatementCQL3(), com.scylladb.utils.ConsistencyLevel.LOCAL_QUORUM);

            client.execute("USE \"" + keyspace + "\"", com.scylladb.utils.ConsistencyLevel.LOCAL_QUORUM);

            //Add standard1
            client.execute(createStandard1StatementCQL3(settings), com.scylladb.utils.ConsistencyLevel.LOCAL_QUORUM);


            if (cmd_type == Command.COUNTER_WRITE) {
                //Add counter1
                client.execute(createCounter1StatementCQL3(settings), com.scylladb.utils.ConsistencyLevel.LOCAL_QUORUM);
            }

            System.out.println(String.format("Created keyspaces. Sleeping %ss for propagation.", settings.node().nodes.size()));
            Thread.sleep(settings.node().nodes.size() * 1000L); // seconds
        } catch (AlreadyExistsException |
                 com.datastax.oss.driver.api.core.servererrors.AlreadyExistsException e) {
            //Ok.
        } catch (Exception e) {
            throw new RuntimeException("Encountered exception creating schema", e);
        }
    }

    String createKeyspaceStatementCQL3() {
        StringBuilder b = new StringBuilder();

        //Create Keyspace
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
            for (ByteBuffer name : settings.columns().names)
                b.append("\n, \"").append(ByteBufferUtil.string(name)).append("\" blob");
        } catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        }

        //Compression
        b.append(") WITH compression = {");
        if (compression != null)
            b.append("'sstable_compression' : '").append(compression).append("'");

        b.append("}");

        //Compaction
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
            for (ByteBuffer name : settings.columns().names)
                b.append("\n, \"").append(ByteBufferUtil.string(name)).append("\" counter");
        } catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        }

        //Compression
        b.append(") WITH compression = {");
        if (compression != null)
            b.append("'sstable_compression' : '").append(compression).append("'");

        b.append("}");

        //Compaction
        if (compactionStrategy != null) {
            b.append(" AND compaction = { 'class' : '").append(compactionStrategy).append("'");

            for (Map.Entry<String, String> entry : compactionStrategyOptions.entrySet())
                b.append(", '").append(entry.getKey()).append("' : '").append(entry.getValue()).append("'");

            b.append("}");
        }

        b.append(";\n");

        return b.toString();
    }

    // Option Declarations

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

    // CLI Utility Methods
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
        return SettingsSchema::printHelp;
    }

}
