// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.SchemaAlreadyExistsException;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressPreparedStatement;
import org.apache.cassandra.stress.driver.TableSchema;
import org.apache.cassandra.stress.driver.TokenSlice;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.RatioDistribution;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.generate.TokenRangeIterator;
import org.apache.cassandra.stress.generate.values.GeneratorConfig;
import org.apache.cassandra.stress.operations.userdefined.SchemaInsert;
import org.apache.cassandra.stress.operations.userdefined.SchemaQuery;
import org.apache.cassandra.stress.operations.userdefined.TokenRangeQuery;
import org.apache.cassandra.stress.operations.userdefined.ValidatingSchemaQuery;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.OptionDistribution;
import org.apache.cassandra.stress.settings.SettingsCommand;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.CqlNames;
import org.apache.cassandra.stress.util.ResultLogger;
import org.apache.cassandra.stress.util.Sleep;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;

public class StressProfile {
    public String specName;
    private String keyspaceCql;
    private String tableCql;
    private List<String> extraSchemaDefinitions;
    private static final String SEED_PREFIX = "seed for stress";

    public String keyspaceName;
    public String tableName;
    private Map<String, GeneratorConfig> columnConfigs;
    private Map<String, StressYaml.QueryDef> queries;
    public Map<String, StressYaml.TokenRangeQueryDef> tokenRangeQueries;
    private Map<String, String> insert;
    private boolean schemaCreated;

    volatile TableSchema tableMetaData;
    volatile List<TokenSlice> tokenRanges;

    volatile ProfileGenerators generators;
    volatile ProfileInsert insertSpec;
    volatile StressPreparedStatement insertStatement;
    volatile List<ValidatingSchemaQuery.Factory> validationFactories;

    volatile Map<String, SchemaQuery.ArgSelect> argSelects;
    volatile Map<String, StressPreparedStatement> queryStatements;

    public void printSettings(ResultLogger out, StressSettings stressSettings) {
        out.printf("  Keyspace Name: %s%n", keyspaceName);
        out.printf("  Keyspace CQL: %n***%n%s***%n%n", keyspaceCql);
        out.printf("  Table Name: %s%n", tableName);
        out.printf("  Table CQL: %n***%n%s***%n%n", tableCql);
        out.printf("  Extra Schema Definitions: %s%n", extraSchemaDefinitions);
        if (columnConfigs != null) {
            out.printf("  Generator Configs:%n");
            columnConfigs.forEach((k, v) -> out.printf("    %s: %s%n", k, v.getConfigAsString()));
        }
        if (queries != null) {
            out.printf("  Query Definitions:%n");
            queries.forEach((k, v) -> out.printf("    %s: %s%n", k, v.getConfigAsString()));
        }
        if (tokenRangeQueries != null) {
            out.printf("  Token Range Queries:%n");
            tokenRangeQueries.forEach((k, v) -> out.printf("    %s: %s%n", k, v.getConfigAsString()));
        }
        if (insert != null) {
            out.printf("  Insert Settings:%n");
            insert.forEach((k, v) -> out.printf("    %s: %s%n", k, v));
        }

        PartitionGenerator generator = newGenerator(stressSettings);
        Distribution visits = stressSettings.insert.visits.get();
        ProfileInsert spec = insertSpec(generator, stressSettings);
        Distribution partitions = spec.partitions().get();
        RatioDistribution selectChance = spec.selectChance().get();

        double minBatchSize =
                selectChance.min() * partitions.minValue() * generator.minRowCount * (1d / visits.maxValue());
        double maxBatchSize =
                selectChance.max() * partitions.maxValue() * generator.maxRowCount * (1d / visits.minValue());
        out.printf(
                "Generating batches with [%d..%d] partitions and [%.0f..%.0f] rows (of [%.0f..%.0f] total rows in the"
                        + " partitions)%n",
                partitions.minValue(),
                partitions.maxValue(),
                minBatchSize,
                maxBatchSize,
                partitions.minValue() * generator.minRowCount,
                partitions.maxValue() * generator.maxRowCount);
    }

    private void init(StressYaml yaml) {
        keyspaceName = yaml.keyspace;
        keyspaceCql = yaml.keyspace_definition;
        tableName = yaml.table;
        tableCql = yaml.table_definition;
        queries = yaml.queries;
        tokenRangeQueries = yaml.token_range_queries;
        insert = yaml.insert;
        specName = yaml.specname;
        if (specName == null) {
            specName = keyspaceName + "." + tableName;
        }

        extraSchemaDefinitions = yaml.extra_definitions;

        if (keyspaceName == null) {
            throw new IllegalArgumentException("keyspace name is required in yaml file");
        }
        if (tableName == null) {
            throw new IllegalArgumentException("table name is required in yaml file");
        }
        if (queries == null) {
            throw new IllegalArgumentException("queries map is required in yaml file");
        }

        for (String query : queries.keySet()) {
            assert !tokenRangeQueries.containsKey(query)
                    : String.format(
                            Locale.ROOT,
                            "Found %s in both queries and token_range_queries, please use different names",
                            query);
            assert !"insert".equals(query)
                    : String.format(
                            Locale.ROOT, "Found 'insert' in queries, this name is reserved, please use different name");
        }
        if (keyspaceCql != null && keyspaceCql.length() > 0) {
            try {
                String name = CqlNames.keyspaceOf(keyspaceCql);
                assert name.equalsIgnoreCase(keyspaceName)
                        : "Name in keyspace_definition doesn't match keyspace property: '" + name + "' != '"
                                + keyspaceName + "'";
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "There was a problem parsing the keyspace cql: " + e.getMessage(), e);
            }
        } else {
            keyspaceCql = null;
        }

        if (tableCql != null && tableCql.length() > 0) {
            try {
                String name = CqlNames.tableOf(tableCql);
                assert name.equalsIgnoreCase(tableName)
                        : "Name in table_definition doesn't match table property: '" + name + "' != '" + tableName
                                + "'";
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("There was a problem parsing the table cql: " + e.getMessage(), e);
            }
        } else {
            tableCql = null;
        }

        columnConfigs = new HashMap<>();

        if (yaml.columnspec != null) {
            for (Map<String, Object> spec : yaml.columnspec) {
                lowerCase(spec);
                String name = (String) spec.remove("name");
                DistributionFactory population = !spec.containsKey("population")
                        ? null
                        : OptionDistribution.get((String) spec.remove("population"));
                DistributionFactory size =
                        !spec.containsKey("size") ? null : OptionDistribution.get((String) spec.remove("size"));
                DistributionFactory clustering =
                        !spec.containsKey("cluster") ? null : OptionDistribution.get((String) spec.remove("cluster"));

                if (!spec.isEmpty()) {
                    throw new IllegalArgumentException("Unrecognised option(s) in column spec: " + spec);
                }
                if (name == null) {
                    throw new IllegalArgumentException("Missing name argument in column spec");
                }

                GeneratorConfig config = new GeneratorConfig(SEED_PREFIX + name, clustering, size, population);
                columnConfigs.put(name.toLowerCase(Locale.ROOT), config);
            }
        }
    }

    @SuppressWarnings("EmptyCatch")
    private static void executeIgnoringExisting(StressClient client, String cql, ConsistencyLevel consistencyLevel) {
        try {
            client.execute(cql, consistencyLevel);
        } catch (SchemaAlreadyExistsException ignored) {
        }
    }

    public void maybeCreateSchema(StressSettings settings) {
        if (!schemaCreated) {
            StressClient client = settings.getClient(false);
            ConsistencyLevel schemaConsistencyLevel = schemaConsistency(settings);

            if (keyspaceCql != null) {
                executeIgnoringExisting(client, keyspaceCql, schemaConsistencyLevel);
            }

            client.execute("use " + keyspaceName, schemaConsistencyLevel);

            if (tableCql != null) {
                executeIgnoringExisting(client, tableCql, schemaConsistencyLevel);

                settings.output()
                        .println(String.format(
                                Locale.ROOT,
                                "Created schema. Sleeping %ss for propagation.",
                                settings.node.nodes.size()));
                Sleep.uninterruptibly(settings.node.nodes.size(), TimeUnit.SECONDS);
            }

            if (extraSchemaDefinitions != null) {
                for (String extraCql : extraSchemaDefinitions) {

                    executeIgnoringExisting(client, extraCql, schemaConsistencyLevel);
                }

                settings.output()
                        .println(String.format(
                                Locale.ROOT,
                                "Created extra schema. Sleeping %ss for propagation.",
                                settings.node.nodes.size()));
                Sleep.uninterruptibly(settings.node.nodes.size(), TimeUnit.SECONDS);
            }
            schemaCreated = true;
        }
        maybeLoadSchemaInfo(settings);
    }

    private static ConsistencyLevel schemaConsistency(StressSettings settings) {
        ConsistencyLevel requested = settings.command.consistencyLevel;
        boolean preferLocal = (requested != null && requested.isDatacenterLocal()) || settings.node.datacenter != null;
        ConsistencyLevel quorum = preferLocal ? ConsistencyLevel.LOCAL_QUORUM : ConsistencyLevel.QUORUM;

        if (requested == null) {
            return quorum;
        }

        if (requested.isSerialConsistency() || requested == ConsistencyLevel.ANY) {
            return quorum;
        }

        return switch (requested) {
            case ONE, TWO, THREE, LOCAL_ONE -> quorum;
            default -> requested;
        };
    }

    public void truncateTable(StressSettings settings) {
        StressClient client = settings.getClient(false);
        assert settings.command.truncate != SettingsCommand.TruncateWhen.NEVER;
        String cql = String.format(Locale.ROOT, "TRUNCATE %s.%s", keyspaceName, tableName);
        client.execute(cql, ConsistencyLevel.ONE);
        settings.output()
                .println(String.format(
                        Locale.ROOT,
                        "Truncated %s.%s. Sleeping %ss for propagation.",
                        keyspaceName,
                        tableName,
                        settings.node.nodes.size()));
        Sleep.uninterruptibly(settings.node.nodes.size(), TimeUnit.SECONDS);
    }

    private void maybeLoadSchemaInfo(StressSettings settings) {
        if (tableMetaData == null) {
            StressClient client = settings.getClient();
            synchronized (client) {
                if (tableMetaData != null) {
                    return;
                }

                TableSchema metadata = client.tableSchema(keyspaceName, tableName);

                if (metadata == null) {
                    throw new RuntimeException("Unable to find table " + keyspaceName + "." + tableName);
                }

                for (ColumnSchema column : metadata.columns()) {
                    String colName = column.name();
                    if (columnConfigs.containsKey(colName)) {
                        continue;
                    }

                    columnConfigs.put(colName, new GeneratorConfig(SEED_PREFIX + colName, null, null, null));
                }

                tableMetaData = metadata;
            }
        }
    }

    public List<TokenSlice> maybeLoadTokenRanges(StressSettings settings) {
        maybeLoadSchemaInfo(settings);

        StressClient client = settings.getClient(false);
        synchronized (client) {
            if (tokenRanges == null) {
                tokenRanges = client.tokenRanges();
            }
            return tokenRanges;
        }
    }

    public Operation getQuery(
            String name, Timer timer, PartitionGenerator generator, SeedManager seeds, StressSettings settings) {
        name = name.toLowerCase(Locale.ROOT);
        if (!queries.containsKey(name)) {
            throw new IllegalArgumentException("No query defined with name " + name);
        }

        if (queryStatements == null) {
            synchronized (this) {
                if (queryStatements == null) {
                    StressClient client = settings.getClient();

                    Map<String, StressPreparedStatement> stmts = new HashMap<>();
                    Map<String, SchemaQuery.ArgSelect> args = new HashMap<>();
                    for (Map.Entry<String, StressYaml.QueryDef> e : queries.entrySet()) {
                        StressYaml.QueryDef query = e.getValue();
                        StressPreparedStatement stmt = client.prepare(query.cql);
                        String queryName = e.getKey().toLowerCase(Locale.ROOT);

                        if (query.consistencyLevel != null) {
                            stmt.setConsistencyLevel(
                                    ConsistencyLevel.valueOf(query.consistencyLevel.toUpperCase(Locale.ROOT)));
                        } else {
                            stmt.setConsistencyLevel(settings.command.consistencyLevel);
                        }

                        if (query.serialConsistencyLevel != null) {
                            stmt.setSerialConsistencyLevel(
                                    ConsistencyLevel.valueOf(query.serialConsistencyLevel.toUpperCase(Locale.ROOT)));
                        } else {
                            stmt.setSerialConsistencyLevel(settings.command.serialConsistencyLevel);
                        }

                        stmts.put(queryName, stmt);
                        args.put(
                                queryName,
                                query.fields == null
                                        ? SchemaQuery.ArgSelect.MULTIROW
                                        : SchemaQuery.ArgSelect.valueOf(query.fields.toUpperCase(Locale.ROOT)));
                    }
                    queryStatements = stmts;
                    argSelects = args;
                }
            }
        }

        return new SchemaQuery(timer, settings, generator, seeds, queryStatements.get(name), argSelects.get(name));
    }

    public Operation getBulkReadQueries(
            String name,
            Timer timer,
            StressSettings settings,
            TokenRangeIterator tokenRangeIterator,
            boolean isWarmup) {
        StressYaml.TokenRangeQueryDef def = tokenRangeQueries.get(name);
        if (def == null) {
            throw new IllegalArgumentException("No bulk read query defined with name " + name);
        }

        return new TokenRangeQuery(timer, settings, tableMetaData, tokenRangeIterator, def, isWarmup);
    }

    private ProfileInsert insertSpec(PartitionGenerator generator, StressSettings settings) {
        if (insertSpec == null) {
            synchronized (this) {
                if (insertSpec == null) {
                    maybeLoadSchemaInfo(settings);
                    insertSpec = ProfileInsert.of(tableMetaData, insert, generator, settings);
                }
            }
        }
        return insertSpec;
    }

    public SchemaInsert getInsert(
            Timer timer, PartitionGenerator generator, SeedManager seedManager, StressSettings settings) {
        if (insertStatement == null) {
            synchronized (this) {
                if (insertStatement == null) {
                    ProfileInsert spec = insertSpec(generator, settings);
                    StressPreparedStatement statement = settings.getClient().prepare(spec.cql());
                    statement.setConsistencyLevel(spec.consistencyLevel());
                    statement.setSerialConsistencyLevel(spec.serialConsistencyLevel());
                    insertStatement = statement;
                }
            }
        }

        ProfileInsert spec = insertSpec;
        return new SchemaInsert(
                timer,
                settings,
                generator,
                seedManager,
                spec.partitions().get(),
                spec.selectChance().get(),
                spec.rowPopulation().get(),
                insertStatement,
                spec.batchType());
    }

    public List<ValidatingSchemaQuery> getValidate(
            Timer timer, PartitionGenerator generator, SeedManager seedManager, StressSettings settings) {
        if (validationFactories == null) {
            synchronized (this) {
                if (validationFactories == null) {
                    maybeLoadSchemaInfo(settings);
                    validationFactories = ValidatingSchemaQuery.create(tableMetaData, settings);
                }
            }
        }

        List<ValidatingSchemaQuery> queries = new ArrayList<>();
        for (ValidatingSchemaQuery.Factory factory : validationFactories) {
            queries.add(factory.create(
                    timer,
                    settings,
                    generator,
                    seedManager,
                    settings.command.consistencyLevel,
                    settings.command.serialConsistencyLevel));
        }
        return queries;
    }

    public PartitionGenerator newGenerator(StressSettings settings) {
        if (generators == null) {
            synchronized (this) {
                maybeCreateSchema(settings);
                maybeLoadSchemaInfo(settings);
                if (generators == null) {
                    generators =
                            new ProfileGenerators(tableMetaData, columnConfigs, settings.errors.skipUnsupportedColumns);
                }
            }
        }

        return generators.newGenerator(settings);
    }

    public static StressProfile load(URI file) throws IOError {
        try {
            Constructor constructor = new Constructor(StressYaml.class, new LoaderOptions());

            Yaml yaml = new Yaml(constructor);

            StressYaml profileYaml;
            try (InputStream yamlStream = file.toURL().openStream()) {
                if (yamlStream.available() == 0) {
                    throw new IOException("Unable to load yaml file from: " + file);
                }
                profileYaml = yaml.loadAs(yamlStream, StressYaml.class);
            }

            StressProfile profile = new StressProfile();
            profile.init(profileYaml);

            return profile;
        } catch (YAMLException | IOException e) {
            throw new IOError(e);
        }
    }

    static <V> void lowerCase(Map<String, V> map) {
        Map<String, V> lowered = new LinkedHashMap<>();
        map.entrySet().removeIf(e -> {
            String lower = e.getKey().toLowerCase(Locale.ROOT);
            if (lower.equals(e.getKey())) {
                return false;
            }
            lowered.put(lower, e.getValue());
            return true;
        });
        map.putAll(lowered);
    }
}
