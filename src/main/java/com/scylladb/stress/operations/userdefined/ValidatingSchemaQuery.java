package com.scylladb.stress.operations.userdefined;
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


import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.datastax.driver.core.*;
import com.scylladb.stress.core.PreparedStatement;
import com.scylladb.stress.core.BoundStatement;
import com.scylladb.utils.ConsistencyLevel;
import com.scylladb.stress.generate.*;
import com.scylladb.stress.generate.Row;
import com.scylladb.stress.core.TableMetadata;
import com.scylladb.stress.operations.PartitionOperation;
import com.scylladb.stress.report.Timer;
import com.scylladb.stress.settings.StressSettings;
import com.scylladb.stress.util.JavaDriverClient;
import com.scylladb.stress.util.JavaDriverV4Client;
import com.scylladb.utils.Pair;

public class ValidatingSchemaQuery extends PartitionOperation {
    private Pair<Row, Row> bounds;

    final int clusteringComponents;
    final ValidatingStatement[] statements;
    final ConsistencyLevel cl;
    final int[] argumentIndex;
    final Object[] bindBuffer;

    private ValidatingSchemaQuery(Timer timer, StressSettings settings, PartitionGenerator generator, SeedManager seedManager, ValidatingStatement[] statements, ConsistencyLevel cl, ConsistencyLevel serialCl, int clusteringComponents) {
        super(timer, settings, new DataSpec(generator, seedManager, new DistributionFixed(1), settings.insert().rowPopulationRatio.get(), 1));
        this.statements = statements;
        this.cl = cl;
        argumentIndex = new int[statements[0].statement.getVariables().size()];
        bindBuffer = new Object[argumentIndex.length];
        int i = 0;
        for (String columnName : statements[0].statement.getColumnNames())
            argumentIndex[i++] = spec.partitionGenerator.indexOf(columnName);

        for (ValidatingStatement statement : statements) {
            if (statement.statement.getConsistencyLevel() == null)
                statement.statement.setConsistencyLevel(cl);
            if (statement.statement.getSerialConsistencyLevel() == null)
                statement.statement.setSerialConsistencyLevel(serialCl);
        }
        this.clusteringComponents = clusteringComponents;
    }

    protected boolean reset(Seed seed, PartitionIterator iterator) {
        bounds = iterator.resetToBounds(seed, clusteringComponents);
        return true;
    }

    abstract class Runner implements RunOp {
        int partitionCount;
        int rowCount;
        final PartitionIterator iter;
        final int statementIndex;

        protected Runner(PartitionIterator iter) {
            this.iter = iter;
            statementIndex = ThreadLocalRandom.current().nextInt(statements.length);
        }

        @Override
        public int partitionCount() {
            return partitionCount;
        }

        @Override
        public int rowCount() {
            return rowCount;
        }
    }

    private class JavaDriverRun extends Runner {
        final JavaDriverClient client;

        private JavaDriverRun(JavaDriverClient client, PartitionIterator iter) {
            super(iter);
            this.client = client;
        }

        public boolean run() throws Exception {
            ResultSet rs = client.getSession().execute(bind(statementIndex).ToV3Value());
            int[] valueIndex = new int[rs.getColumnDefinitions().size()];
            {
                int i = 0;
                for (ColumnDefinitions.Definition definition : rs.getColumnDefinitions())
                    valueIndex[i++] = spec.partitionGenerator.indexOf(definition.getName());
            }

            rowCount = 0;
            Iterator<com.datastax.driver.core.Row> results = rs.iterator();
            if (!statements[statementIndex].inclusiveStart && iter.hasNext())
                iter.next();
            while (iter.hasNext()) {
                Row expectedRow = iter.next();
                if (!statements[statementIndex].inclusiveEnd && !iter.hasNext())
                    break;

                if (!results.hasNext())
                    return false;

                rowCount++;
                com.datastax.driver.core.Row actualRow = results.next();
                for (int i = 0; i < actualRow.getColumnDefinitions().size(); i++) {
                    Object expectedValue = expectedRow.get(valueIndex[i]);
                    Object actualValue = spec.partitionGenerator.convert(valueIndex[i], actualRow.getBytesUnsafe(i));
                    if (!expectedValue.equals(actualValue))
                        return false;
                }
            }
            partitionCount = Math.min(1, rowCount);
            return rs.isExhausted();
        }
    }

    private class JavaDriverV4Run extends Runner {
        final JavaDriverV4Client client;

        private JavaDriverV4Run(JavaDriverV4Client client, PartitionIterator iter) {
            super(iter);
            this.client = client;
        }

        public boolean run() throws Exception {
            com.datastax.oss.driver.api.core.cql.ResultSet rs = client.getSession().execute(bind(statementIndex).ToV4Value());
            int[] valueIndex = new int[rs.getColumnDefinitions().size()];
            {
                int i = 0;
                for (com.datastax.oss.driver.api.core.cql.ColumnDefinition definition : rs.getColumnDefinitions())
                    valueIndex[i++] = spec.partitionGenerator.indexOf(definition.getName().toString());
            }

            rowCount = 0;
            Iterator<com.datastax.oss.driver.api.core.cql.Row> results = rs.iterator();
            if (!statements[statementIndex].inclusiveStart && iter.hasNext())
                iter.next();
            while (iter.hasNext()) {
                Row expectedRow = iter.next();
                if (!statements[statementIndex].inclusiveEnd && !iter.hasNext())
                    break;

                if (!results.hasNext())
                    return false;

                rowCount++;
                com.datastax.oss.driver.api.core.cql.Row actualRow = results.next();
                for (int i = 0; i < actualRow.getColumnDefinitions().size(); i++) {
                    Object expectedValue = expectedRow.get(valueIndex[i]);
                    Object actualValue = spec.partitionGenerator.convert(valueIndex[i], actualRow.getBytesUnsafe(i));
                    if (!expectedValue.equals(actualValue))
                        return false;
                }
            }
            partitionCount = Math.min(1, rowCount);
            return rs.isFullyFetched();
        }
    }

    BoundStatement bind(int statementIndex) {
        int pkc = bounds.left.partitionKey().length;
        System.arraycopy(bounds.left.partitionKey(), 0, bindBuffer, 0, pkc);
        int ccc = bounds.left.row().length;
        System.arraycopy(bounds.left.row(), 0, bindBuffer, pkc, ccc);
        System.arraycopy(bounds.right.row(), 0, bindBuffer, pkc + ccc, ccc);
        return statements[statementIndex].statement.bind(bindBuffer);
    }

    @Override
    public void run(JavaDriverClient client) throws IOException {
        timeWithRetry(new JavaDriverRun(client, partitions.getFirst()));
    }

    @Override
    public void run(JavaDriverV4Client client) throws IOException {
        timeWithRetry(new JavaDriverV4Run(client, partitions.getFirst()));
    }

    public static class Factory {
        final ValidatingStatement[] statements;
        final int clusteringComponents;

        public Factory(ValidatingStatement[] statements, int clusteringComponents) {
            this.statements = statements;
            this.clusteringComponents = clusteringComponents;
        }

        public ValidatingSchemaQuery create(Timer timer, StressSettings settings, PartitionGenerator generator, SeedManager seedManager, ConsistencyLevel cl, ConsistencyLevel serialCl) {
            return new ValidatingSchemaQuery(timer, settings, generator, seedManager, statements, cl, serialCl, clusteringComponents);
        }
    }

    public static List<Factory> create(TableMetadata metadata, StressSettings settings) {
        List<Factory> factories = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        sb.append("SELECT * FROM ");
        sb.append(metadata.getName());
        sb.append(" WHERE");
        for (String pkName : metadata.getPartitionKeyNames()) {
            sb.append(first ? " " : " AND ");
            sb.append(pkName);
            sb.append(" = ?");
            first = false;
        }
        String base = sb.toString();

        factories.add(new Factory(new ValidatingStatement[]{prepare(settings, base, true, true)}, 0));

        List<String> clusteringColumnNames = metadata.getClusteringColumnNames();

        int maxDepth = clusteringColumnNames.size() - 1;
        for (int depth = 0; depth <= maxDepth; depth++) {
            StringBuilder cc = new StringBuilder();
            StringBuilder arg = new StringBuilder();
            cc.append('(');
            arg.append('(');
            for (int d = 0; d <= depth; d++) {
                if (d > 0) {
                    cc.append(',');
                    arg.append(',');
                }
                cc.append(clusteringColumnNames.get(d));
                arg.append('?');
            }
            cc.append(')');
            arg.append(')');

            ValidatingStatement[] statements = new ValidatingStatement[depth < maxDepth ? 1 : 4];
            int i = 0;
            for (boolean incLb : depth < maxDepth ? new boolean[]{true} : new boolean[]{true, false}) {
                for (boolean incUb : depth < maxDepth ? new boolean[]{false} : new boolean[]{true, false}) {
                    String lb = incLb ? ">=" : ">";
                    String ub = incUb ? "<=" : "<";
                    sb.setLength(0);
                    sb.append(base);
                    sb.append(" AND ");
                    sb.append(cc);
                    sb.append(lb);
                    sb.append(arg);
                    sb.append(" AND ");
                    sb.append(cc);
                    sb.append(ub);
                    sb.append(arg);
                    statements[i++] = prepare(settings, sb.toString(), incLb, incUb);
                }
            }
            factories.add(new Factory(statements, depth + 1));
        }

        return factories;
    }

    private record ValidatingStatement(PreparedStatement statement, boolean inclusiveStart, boolean inclusiveEnd) {
    }

    private static ValidatingStatement prepare(StressSettings settings, String cql, boolean incLb, boolean incUb) {
        return switch (settings.mode().api) {
            case JAVA_DRIVER4_NATIVE ->
                    new ValidatingStatement(settings.getJavaDriverV4Client().prepare(cql), incLb, incUb);
            case JAVA_DRIVER_NATIVE ->
                    new ValidatingStatement(settings.getJavaDriverClient().prepare(cql), incLb, incUb);
        };
    }
}
