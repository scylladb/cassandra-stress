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
package com.scylladb.stress;


import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Uninterruptibles;

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.AlreadyExistsException;
import com.scylladb.stress.core.BatchStatementType;
import com.scylladb.stress.core.ColumnMetadata;
import com.scylladb.stress.core.PreparedStatement;
import com.scylladb.stress.core.TableMetadata;
import com.scylladb.stress.generate.*;
import com.scylladb.stress.generate.values.*;
import com.scylladb.stress.operations.userdefined.TokenRangeQuery;
import com.scylladb.stress.operations.userdefined.SchemaInsert;
import com.scylladb.stress.operations.userdefined.SchemaQuery;
import com.scylladb.stress.operations.userdefined.ValidatingSchemaQuery;
import com.scylladb.stress.report.Timer;
import com.scylladb.stress.settings.*;
import com.scylladb.stress.util.JavaDriverClient;
import com.scylladb.stress.util.JavaDriverV4Client;
import com.scylladb.stress.util.MetadataProvider;
import com.scylladb.stress.util.QueryExecutor;
import com.scylladb.stress.util.QueryPrepare;
import com.scylladb.stress.util.ResultLogger;
import com.scylladb.utils.ConsistencyLevel;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;

public class StressProfile implements Serializable {
    public String specName;
    private String keyspaceCql;
    private String tableCql;
    private List<String> extraSchemaDefinitions;
    public final String seedStr = "seed for stress";

    public String keyspaceName;
    public String tableName;
    private Map<String, GeneratorConfig> columnConfigs;
    private Map<String, StressYaml.QueryDef> queries;
    public Map<String, StressYaml.TokenRangeQueryDef> tokenRangeQueries;
    private Map<String, String> insert;
    private boolean schemaCreated = false;

    transient volatile TableMetadata tableMetaData;
    transient volatile Set<TokenRange> tokenRanges;

    transient volatile GeneratorFactory generatorFactory;

    transient volatile BatchStatementType batchType;
    transient volatile DistributionFactory partitions;
    transient volatile RatioDistributionFactory selectchance;
    transient volatile RatioDistributionFactory rowPopulation;
    transient volatile ConsistencyLevel consistencyLevel;
    transient volatile ConsistencyLevel serialConsistencyLevel;
    transient volatile PreparedStatement insertStatement;
    transient volatile String query;
    transient volatile List<ValidatingSchemaQuery.Factory> validationFactories;

    transient volatile Map<String, SchemaQuery.ArgSelect> argSelects;
    transient volatile Map<String, PreparedStatement> queryStatements;

    private static final Pattern lowercaseAlphanumeric = Pattern.compile("[a-z0-9_]+");


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
        Distribution visits = stressSettings.insert().visits.get();
        prepareQuery(generator, stressSettings); //just calling this to initialize selectchance and partitions vals for calc below

        double minBatchSize = selectchance.get().min() * partitions.get().minValue() * generator.minRowCount * (1d / visits.maxValue());
        double maxBatchSize = selectchance.get().max() * partitions.get().maxValue() * generator.maxRowCount * (1d / visits.minValue());
        out.printf("Generating batches with [%d..%d] partitions and [%.0f..%.0f] rows (of [%.0f..%.0f] total rows in the partitions)%n",
                partitions.get().minValue(), partitions.get().maxValue(),
                minBatchSize, maxBatchSize,
                partitions.get().minValue() * generator.minRowCount,
                partitions.get().maxValue() * generator.maxRowCount);
    }


    private void init(StressYaml yaml) throws RequestValidationException {
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

        assert keyspaceName != null : "keyspace name is required in yaml file";
        assert tableName != null : "table name is required in yaml file";
        assert queries != null : "queries map is required in yaml file";

        for (String query : queries.keySet()) {
            assert !tokenRangeQueries.containsKey(query) : String.format("Found %s in both queries and token_range_queries, please use different names", query);
            assert !query.equals("insert") : "Found 'insert' in queries, this name is reserved, please use different name";
        }
        if (keyspaceCql != null && !keyspaceCql.isEmpty()) {
            try {
                String name = CQLFragmentParser.parseAnyUnhandled(CqlParser::createKeyspaceStatement, keyspaceCql).keyspace();
                assert name.equalsIgnoreCase(keyspaceName) : "Name in keyspace_definition doesn't match keyspace property: '" + name + "' != '" + keyspaceName + "'";
            } catch (RecognitionException | SyntaxException e) {
                throw new IllegalArgumentException("There was a problem parsing the keyspace cql: " + e.getMessage());
            }
        } else {
            keyspaceCql = null;
        }

        if (tableCql != null && !tableCql.isEmpty()) {
            try {
                String name = CQLFragmentParser.parseAnyUnhandled(CqlParser::createTableStatement, tableCql).columnFamily();
                assert name.equalsIgnoreCase(tableName) : "Name in table_definition doesn't match table property: '" + name + "' != '" + tableName + "'";
            } catch (RecognitionException | RuntimeException e) {
                throw new IllegalArgumentException("There was a problem parsing the table cql: " + e.getMessage());
            }
        } else {
            tableCql = null;
        }

        columnConfigs = new HashMap<>();

        if (yaml.columnspec != null) {
            for (Map<String, Object> spec : yaml.columnspec) {
                lowerCase(spec);
                String name = (String) spec.remove("name");
                DistributionFactory population = !spec.containsKey("population") ? null : OptionDistribution.get((String) spec.remove("population"));
                DistributionFactory size = !spec.containsKey("size") ? null : OptionDistribution.get((String) spec.remove("size"));
                DistributionFactory clustering = !spec.containsKey("cluster") ? null : OptionDistribution.get((String) spec.remove("cluster"));

                if (!spec.isEmpty())
                    throw new IllegalArgumentException("Unrecognised option(s) in column spec: " + spec);
                if (name == null)
                    throw new IllegalArgumentException("Missing name argument in column spec");

                GeneratorConfig config = new GeneratorConfig(seedStr + name, clustering, size, population);
                columnConfigs.put(name, config);
            }
        }
    }

    public void maybeCreateSchema(StressSettings settings) {
        if (!schemaCreated) {
            QueryExecutor client;
            if (settings.mode().api == ConnectionAPI.JAVA_DRIVER4_NATIVE) {
                client = settings.getJavaDriverV4Client(false);
            } else {
                client = settings.getJavaDriverClient(false);
            }

            ConsistencyLevel schemaConsistencyLevel = schemaConsistency(settings);

            if (keyspaceCql != null) {
                try {
                    client.execute(keyspaceCql, schemaConsistencyLevel);
                } catch (AlreadyExistsException |
                         com.datastax.oss.driver.api.core.servererrors.AlreadyExistsException ignored) {
                }
            }

            client.execute("use " + keyspaceName, schemaConsistencyLevel);

            if (tableCql != null) {
                try {
                    client.execute(tableCql, schemaConsistencyLevel);
                } catch (AlreadyExistsException |
                         com.datastax.oss.driver.api.core.servererrors.AlreadyExistsException ignored) {
                }

                System.out.printf("Created schema. Sleeping %ss for propagation.%n", settings.node().nodes.size());
                Uninterruptibles.sleepUninterruptibly(settings.node().nodes.size(), TimeUnit.SECONDS);
            }

            if (extraSchemaDefinitions != null) {
                for (String extraCql : extraSchemaDefinitions) {

                    try {
                        client.execute(extraCql, schemaConsistencyLevel);
                    } catch (AlreadyExistsException |
                             com.datastax.oss.driver.api.core.servererrors.AlreadyExistsException ignored) {
                    }
                }

                System.out.printf("Created extra schema. Sleeping %ss for propagation.%n", settings.node().nodes.size());
                Uninterruptibles.sleepUninterruptibly(settings.node().nodes.size(), TimeUnit.SECONDS);
            }
            schemaCreated = true;
        }
        maybeLoadSchemaInfo(settings);
    }

    private static ConsistencyLevel schemaConsistency(StressSettings settings) {
        ConsistencyLevel requested = settings.command().consistencyLevel;
        boolean preferLocal = (requested != null && requested.isDatacenterLocal()) || settings.node().datacenter != null;
        ConsistencyLevel quorum = preferLocal ? ConsistencyLevel.LOCAL_QUORUM : ConsistencyLevel.QUORUM;

        if (requested == null)
            return quorum;

        if (requested.isSerialConsistency() || requested == ConsistencyLevel.ANY)
            return quorum;

        return switch (requested) {
            case ONE, TWO, THREE, LOCAL_ONE -> quorum;
            default -> requested;
        };
    }

    public void truncateTable(StressSettings settings) {
        QueryExecutor client;
        if (settings.mode().api == ConnectionAPI.JAVA_DRIVER4_NATIVE) {
            client = settings.getJavaDriverV4Client(false);
        } else {
            client = settings.getJavaDriverClient(false);
        }
        assert settings.command().truncate != SettingsCommand.TruncateWhen.NEVER;
        String cql = String.format("TRUNCATE %s.%s", keyspaceName, tableName);
        client.execute(cql, com.scylladb.utils.ConsistencyLevel.ONE);
        System.out.printf("Truncated %s.%s. Sleeping %ss for propagation.%n",
                keyspaceName, tableName, settings.node().nodes.size());
        Uninterruptibles.sleepUninterruptibly(settings.node().nodes.size(), TimeUnit.SECONDS);
    }

    private void maybeLoadSchemaInfo(StressSettings settings) {
        if (tableMetaData == null) {
            MetadataProvider client;
            if (settings.mode().api == ConnectionAPI.JAVA_DRIVER4_NATIVE) {
                client = settings.getJavaDriverV4Client();
            } else {
                client = settings.getJavaDriverClient();
            }
            synchronized (client) {

                if (tableMetaData != null)
                    return;

                TableMetadata metadata = client.getTableMetadata(keyspaceName, tableName);

                if (metadata == null)
                    throw new RuntimeException("Unable to find table " + keyspaceName + "." + tableName);

                //Fill in missing column configs
                for (String colName : metadata.getColumnNames()) {
                    if (columnConfigs.containsKey(colName))
                        continue;

                    columnConfigs.put(colName, new GeneratorConfig(seedStr + colName, null, null, null));
                }

                tableMetaData = metadata;
            }
        }
    }

    private Set<TokenRange> loadTokenRangesV4(StressSettings settings) {
        JavaDriverV4Client v4 = settings.getJavaDriverV4Client(false);
        synchronized (v4) {
            if (tokenRanges != null)
                return tokenRanges;

            com.datastax.oss.driver.api.core.metadata.Metadata metadata = v4.getSession().getMetadata();
            if (metadata == null)
                throw new RuntimeException("Unable to get metadata");

            com.datastax.oss.driver.api.core.metadata.TokenMap tokenMap =
                    metadata.getTokenMap().orElseThrow(() -> new RuntimeException("Unable to get token map"));

            java.util.Set<com.datastax.oss.driver.api.core.metadata.token.TokenRange> v4Ranges = tokenMap.getTokenRanges();

            // Convert v4 token ranges to v3 TokenRange objects.
            // We use v3 Metadata.newToken(String) which parses according to the cluster partitioner.
            com.datastax.driver.core.Metadata v3Metadata = settings.getJavaDriverClient(false).getCluster().getMetadata();

            java.util.List<TokenRange> sortedRanges = new java.util.ArrayList<>(v4Ranges.size() + 1);
            for (com.datastax.oss.driver.api.core.metadata.token.TokenRange r : v4Ranges) {
                com.datastax.driver.core.Token start = v3Metadata.newToken(r.getStart().toString());
                com.datastax.driver.core.Token end = v3Metadata.newToken(r.getEnd().toString());
                TokenRange range = v3Metadata.newTokenRange(start, end);

                if (range.isWrappedAround())
                    sortedRanges.addAll(range.unwrap());
                else
                    sortedRanges.add(range);
            }

            java.util.Collections.sort(sortedRanges);
            tokenRanges = new java.util.LinkedHashSet<>(sortedRanges);
            return tokenRanges;
        }
    }

    private Set<TokenRange> loadTokenRangesV3(StressSettings settings) {
        JavaDriverClient client = settings.getJavaDriverClient(false);
        synchronized (client) {
            if (tokenRanges != null) {
                return tokenRanges;
            }

            Cluster cluster = client.getCluster();
            Metadata metadata = cluster.getMetadata();
            if (metadata == null) {
                throw new RuntimeException("Unable to get metadata");
            }

            List<TokenRange> sortedRanges = new ArrayList<>(metadata.getTokenRanges().size() + 1);
            for (TokenRange range : metadata.getTokenRanges()) {
                // if we don't unwrap we miss the partitions between ring min and smallest range start value
                if (range.isWrappedAround()) {
                    sortedRanges.addAll(range.unwrap());
                } else {
                    sortedRanges.add(range);
                }
            }

            Collections.sort(sortedRanges);
            tokenRanges = new LinkedHashSet<>(sortedRanges);
            return tokenRanges;
        }
    }

    public Set<TokenRange> maybeLoadTokenRanges(StressSettings settings) {
        maybeLoadSchemaInfo(settings); // ensure table metadata is available

        if (settings.mode().api == ConnectionAPI.JAVA_DRIVER4_NATIVE) {
            try {
                return loadTokenRangesV4(settings);
            } catch (Throwable t) {
                // Fall back to v3 token ranges if v4 is not available.
            }
        }

        return loadTokenRangesV3(settings);
    }

    public Operation getQuery(String name,
                              Timer timer,
                              PartitionGenerator generator,
                              SeedManager seeds,
                              StressSettings settings) {
        name = name.toLowerCase();
        if (!queries.containsKey(name))
            throw new IllegalArgumentException("No query defined with name " + name);

        if (queryStatements == null) {
            synchronized (this) {
                if (queryStatements == null) {
                    QueryPrepare client = switch (settings.mode().api) {
                        case JAVA_DRIVER_NATIVE -> settings.getJavaDriverClient();
                        case JAVA_DRIVER4_NATIVE -> settings.getJavaDriverV4Client();
                    };

                    Map<String, PreparedStatement> stmts = new HashMap<>();
                    Map<String, SchemaQuery.ArgSelect> args = new HashMap<>();
                    for (Map.Entry<String, StressYaml.QueryDef> e : queries.entrySet()) {
                        StressYaml.QueryDef query = e.getValue();
                        PreparedStatement stmt = client.prepare(query.cql);
                        String queryName = e.getKey().toLowerCase();

                        if (query.consistencyLevel != null) {
                            stmt.setConsistencyLevel(ConsistencyLevel.valueOf(query.consistencyLevel.toUpperCase()));
                        } else {
                            stmt.setConsistencyLevel(settings.command().consistencyLevel);
                        }

                        if (query.serialConsistencyLevel != null) {
                            stmt.setSerialConsistencyLevel(ConsistencyLevel.valueOf(query.serialConsistencyLevel.toUpperCase()));
                        } else {
                            stmt.setSerialConsistencyLevel(settings.command().serialConsistencyLevel);
                        }

                        stmts.put(queryName, stmt);
                        args.put(queryName, query.fields == null
                                ? SchemaQuery.ArgSelect.MULTIROW
                                : SchemaQuery.ArgSelect.valueOf(query.fields.toUpperCase()));

                    }
                    queryStatements = stmts;
                    argSelects = args;
                }
            }
        }

        return new SchemaQuery(timer, settings, generator, seeds, queryStatements.get(name),
                argSelects.get(name));
    }

    public Operation getBulkReadQueries(String name, Timer timer, StressSettings settings, TokenRangeIterator tokenRangeIterator, boolean isWarmup) {
        StressYaml.TokenRangeQueryDef def = tokenRangeQueries.get(name);
        if (def == null)
            throw new IllegalArgumentException("No bulk read query defined with name " + name);

        return new TokenRangeQuery(timer, settings, tableMetaData, tokenRangeIterator, def, isWarmup);
    }

    public void prepareQuery(PartitionGenerator generator, StressSettings settings) {
        if (query != null) {
            return;
        }

        maybeLoadSchemaInfo(settings);

        Set<ColumnMetadata> keyColumns = com.google.common.collect.Sets.newHashSet(tableMetaData.getPrimaryKey());
        Set<ColumnMetadata> allColumns = com.google.common.collect.Sets.newHashSet(tableMetaData.getColumns());
        boolean isKeyOnlyTable = (keyColumns.size() == allColumns.size());
        //With compact storage
        if (!isKeyOnlyTable && (keyColumns.size() == (allColumns.size() - 1))) {
            com.google.common.collect.Sets.SetView<ColumnMetadata> diff = com.google.common.collect.Sets.difference(allColumns, keyColumns);
            for (ColumnMetadata col : diff) {
                isKeyOnlyTable = col.getName().isEmpty();
                break;
            }
        }

        //Non PK Columns
        StringBuilder sb = new StringBuilder();
        if (!isKeyOnlyTable) {
            sb.append("UPDATE ").append(quoteIdentifier(tableName)).append(" SET ");
            //PK Columns
            StringBuilder pred = new StringBuilder();
            pred.append(" WHERE ");

            boolean firstCol = true;
            boolean firstPred = true;
            for (ColumnMetadata c : allColumns) {
                if (!c.getType().isSupported())
                    continue;

                if (keyColumns.contains(c)) {
                    if (firstPred)
                        firstPred = false;
                    else
                        pred.append(" AND ");

                    pred.append(quoteIdentifier(c.getName())).append(" = ?");
                } else {
                    if (firstCol)
                        firstCol = false;
                    else
                        sb.append(',');

                    sb.append(quoteIdentifier(c.getName())).append(" = ");

                    switch (c.getType().getName()) {
                        case "SET":
                        case "LIST":
                            if (c.getType().isFrozen()) {
                                sb.append("?");
                                break;
                            }
                        case "COUNTER":
                            sb.append(quoteIdentifier(c.getName())).append(" + ?");
                            break;
                        default:
                            sb.append("?");
                            break;
                    }
                }
            }

            //Put PK predicates at the end
            sb.append(pred);
        } else {
            sb.append("INSERT INTO ").append(quoteIdentifier(tableName)).append(" (");
            StringBuilder value = new StringBuilder();
            for (String colName : tableMetaData.getPrimaryKeyNames()) {
                sb.append(quoteIdentifier(colName)).append(", ");
                value.append("?, ");
            }
            sb.delete(sb.lastIndexOf(","), sb.length());
            value.delete(value.lastIndexOf(","), value.length());
            sb.append(") ").append("values(").append(value).append(')');
        }

        if (insert == null)
            insert = new HashMap<>();
        lowerCase(insert);

        partitions = select(settings.insert().batchsize, "partitions", "fixed(1)", insert, OptionDistribution.BUILDER);
        selectchance = select(settings.insert().selectRatio, "select", "fixed(1)/1", insert, OptionRatioDistribution.BUILDER);
        rowPopulation = select(settings.insert().rowPopulationRatio, "row-population", "fixed(1)/1", insert, OptionRatioDistribution.BUILDER);
        consistencyLevel = selectConsistency(settings.insert().consistencyLevel, "consistencyLevel", settings.command().consistencyLevel, insert);
        serialConsistencyLevel = selectConsistency(settings.insert().serialConsistencyLevel, "serialConsistencyLevel", settings.command().serialConsistencyLevel, insert);
        batchType = settings.insert().batchType != null
                ? settings.insert().batchType
                : !insert.containsKey("batchtype")
                ? BatchStatementType.LOGGED
                : BatchStatementType.valueOf(insert.remove("batchtype"));
        if (!insert.isEmpty())
            throw new IllegalArgumentException("Unrecognised insert option(s): " + insert);

        Distribution visits = settings.insert().visits.get();
        // these min/max are not absolutely accurate if selectchance < 1, but they're close enough to
        // guarantee the vast majority of actions occur in these bounds
        double maxBatchSize = selectchance.get().max() * partitions.get().maxValue() * generator.maxRowCount * (1d / visits.minValue());

        if (generator.maxRowCount > 100 * 1000 * 1000)
            System.err.printf("WARNING: You have defined a schema that permits very large partitions (%.0f max rows (>100M))%n", generator.maxRowCount);
        if (batchType == BatchStatementType.LOGGED && maxBatchSize > 65535) {
            System.err.printf("ERROR: You have defined a workload that generates batches with more than 65k rows (%.0f), but have required the use of LOGGED batches. There is a 65k row limit on a single batch.%n",
                    selectchance.get().max() * partitions.get().maxValue() * generator.maxRowCount);
            System.exit(1);
        }
        if (maxBatchSize > 100000)
            System.err.printf("WARNING: You have defined a schema that permits very large batches (%.0f max rows (>100K)). This may OOM this stress client, or the server.%n",
                    selectchance.get().max() * partitions.get().maxValue() * generator.maxRowCount);

        query = sb.toString();
    }

    public SchemaInsert getInsert(Timer timer, PartitionGenerator generator, SeedManager seedManager, StressSettings settings) {
        if (insertStatement == null) {
            synchronized (this) {
                if (insertStatement == null) {
                    prepareQuery(generator, settings);
                    QueryPrepare client = switch (settings.mode().api) {
                        case JAVA_DRIVER_NATIVE -> settings.getJavaDriverClient();
                        case JAVA_DRIVER4_NATIVE -> settings.getJavaDriverV4Client();
                    };

                    insertStatement = client.prepare(query);
                    insertStatement.setConsistencyLevel(consistencyLevel);
                    insertStatement.setSerialConsistencyLevel(serialConsistencyLevel);
                }
            }
        }

        return new SchemaInsert(timer, settings, generator, seedManager, partitions.get(), selectchance.get(), rowPopulation.get(), insertStatement, batchType);
    }

    public List<ValidatingSchemaQuery> getValidate(Timer timer, PartitionGenerator generator, SeedManager seedManager, StressSettings settings) {
        if (validationFactories == null) {
            synchronized (this) {
                if (validationFactories == null) {
                    maybeLoadSchemaInfo(settings);
                    validationFactories = ValidatingSchemaQuery.create(tableMetaData, settings);
                }
            }
        }

        List<ValidatingSchemaQuery> queries = new ArrayList<>();
        for (ValidatingSchemaQuery.Factory factory : validationFactories)
            queries.add(factory.create(timer, settings, generator, seedManager, settings.command().consistencyLevel, settings.command().serialConsistencyLevel));
        return queries;
    }

    private static <E> E select(E first, String key, String defValue, Map<String, String> map, Function<String, E> builder) {
        String val = map.remove(key);

        if (first != null)
            return first;
        if (val != null && val.trim().length() > 0)
            return builder.apply(val);

        return builder.apply(defValue);
    }

    private static ConsistencyLevel selectConsistency(ConsistencyLevel first, String key, ConsistencyLevel defValue, Map<String, String> map) {
        String val = map.remove(key);

        if (first != null)
            return first;
        if (val != null && !val.trim().isEmpty())
            return ConsistencyLevel.valueOf(val.toUpperCase());

        return defValue;
    }

    public PartitionGenerator newGenerator(StressSettings settings) {
        if (generatorFactory == null) {
            synchronized (this) {
                maybeCreateSchema(settings);
                maybeLoadSchemaInfo(settings);
                if (generatorFactory == null)
                    generatorFactory = new GeneratorFactory(settings);
            }
        }

        return generatorFactory.newGenerator(settings);
    }

    private class GeneratorFactory {
        final List<ColumnInfo> partitionKeys = new ArrayList<>();
        final List<ColumnInfo> clusteringColumns = new ArrayList<>();
        final List<ColumnInfo> valueColumns = new ArrayList<>();

        private GeneratorFactory(StressSettings settings) {
            List<ColumnInfo> unsupportedColumns = new ArrayList<>();
            List<ColumnInfo> unsupportedCriticalColumns = new ArrayList<>();
            Set<ColumnMetadata> keyColumns = com.google.common.collect.Sets.newHashSet(tableMetaData.getPrimaryKey());

            for (ColumnMetadata metadata : tableMetaData.getPartitionKey())
                pushColumnInfo(metadata, partitionKeys, true, unsupportedColumns, unsupportedCriticalColumns);
            for (ColumnMetadata metadata : tableMetaData.getClusteringColumns())
                pushColumnInfo(metadata, clusteringColumns, true, unsupportedColumns, unsupportedCriticalColumns);
            for (ColumnMetadata metadata : tableMetaData.getColumns()) {
                if (!keyColumns.contains(metadata))
                    pushColumnInfo(metadata, valueColumns, !(settings.errors().skipUnsupportedColumns), unsupportedColumns, unsupportedCriticalColumns);
            }
            if (!unsupportedColumns.isEmpty()) {
                for (ColumnInfo column : unsupportedColumns) {
                    System.err.printf("WARNING: Table '%s' has column '%s' of unsupported type\n",
                            tableName, column.name);
                }
            }
            if (!unsupportedCriticalColumns.isEmpty()) {
                for (ColumnInfo column : unsupportedCriticalColumns) {
                    System.err.printf("ERROR: Table '%s' has column '%s' of unsupported type\n",
                            tableName, column.name);
                }
                assert false : "Can't continue due to the errors";
            }
        }

        PartitionGenerator newGenerator(StressSettings settings) {
            return new PartitionGenerator(get(partitionKeys), get(clusteringColumns), get(valueColumns), settings.generate().order);
        }

        List<Generator> get(List<ColumnInfo> columnInfos) {
            List<Generator> result = new ArrayList<>();
            for (ColumnInfo columnInfo : columnInfos)
                result.add(columnInfo.getGenerator());
            return result;
        }

        void pushColumnInfo(ColumnMetadata metadata, List<ColumnInfo> targetList, boolean isCritical,
                            List<ColumnInfo> unsupportedColumns, List<ColumnInfo> unsupportedCriticalColumns) {
            ColumnInfo column = new ColumnInfo(metadata.getName(), metadata.getType().getName().toLowerCase(),
                    metadata.getType().getCollectionElementTypeName().toLowerCase(),
                    columnConfigs.get(metadata.getName()));
            if (!metadata.getType().isSupported()) {
                if (isCritical) {
                    unsupportedCriticalColumns.add(column);
                } else {
                    unsupportedColumns.add(column);
                }
                return;
            }
            targetList.add(column);
        }
    }

    record ColumnInfo(String name, String type, String collectionType, GeneratorConfig config) {

        Generator getGenerator() {
            return getGenerator(name, type, collectionType, config);
        }

        static Generator getGenerator(final String name, final String type, final String collectionType, GeneratorConfig config) {
            return switch (type.toUpperCase()) {
                case "ASCII", "TEXT", "VARCHAR" -> new Strings(name, config);
                case "BIGINT", "COUNTER" -> new Longs(name, config);
                case "BLOB" -> new Bytes(name, config);
                case "BOOLEAN" -> new Booleans(name, config);
                case "DECIMAL" -> new BigDecimals(name, config);
                case "DOUBLE" -> new Doubles(name, config);
                case "FLOAT" -> new Floats(name, config);
                case "INET" -> new Inets(name, config);
                case "INT" -> new Integers(name, config);
                case "VARINT" -> new BigIntegers(name, config);
                case "TIMESTAMP" -> new Dates(name, config);
                case "UUID" -> new UUIDs(name, config);
                case "TIMEUUID" -> new TimeUUIDs(name, config);
                case "TINYINT" -> new TinyInts(name, config);
                case "SMALLINT" -> new SmallInts(name, config);
                case "TIME" -> new Times(name, config);
                case "DATE" -> new LocalDates(name, config);
                case "SET" -> new Sets(name, getGenerator(name, collectionType, null, config), config);
                case "LIST" -> new Lists(name, getGenerator(name, collectionType, null, config), config);
                default ->
                        throw new UnsupportedOperationException("Because of this name: " + name + " if you removed it from the yaml and are still seeing this, make sure to drop table");
            };
        }
    }

    public static StressProfile load(URI file) throws IOError {
        try {
            Constructor constructor = new Constructor(StressYaml.class, new LoaderOptions());

            Yaml yaml = new Yaml(constructor);

            InputStream yamlStream = file.toURL().openStream();

            if (yamlStream.available() == 0)
                throw new IOException("Unable to load yaml file from: " + file);

            StressYaml profileYaml = yaml.loadAs(yamlStream, StressYaml.class);

            StressProfile profile = new StressProfile();
            profile.init(profileYaml);

            return profile;
        } catch (YAMLException | IOException | RequestValidationException e) {
            throw new IOError(e);
        }
    }

    static <V> void lowerCase(Map<String, V> map) {
        List<Map.Entry<String, V>> reinsert = new ArrayList<>();
        Iterator<Map.Entry<String, V>> iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, V> e = iter.next();
            if (!e.getKey().equalsIgnoreCase(e.getKey())) {
                reinsert.add(e);
                iter.remove();
            }
        }
        for (Map.Entry<String, V> e : reinsert)
            map.put(e.getKey().toLowerCase(), e.getValue());
    }

    /* Quote a identifier if it contains uppercase letters */
    private static String quoteIdentifier(String identifier) {
        return lowercaseAlphanumeric.matcher(identifier).matches() ? identifier : '\"' + identifier + '\"';
    }
}
