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
import java.util.List;

import com.scylladb.stress.core.BatchStatementType;
import com.scylladb.stress.core.PreparedStatement;
import com.scylladb.stress.generate.*;
import com.scylladb.stress.report.Timer;
import com.scylladb.stress.settings.StressSettings;
import com.scylladb.stress.util.JavaDriverClient;
import com.scylladb.stress.util.JavaDriverV4Client;
import com.datastax.oss.driver.api.core.cql.BatchableStatement;

public class SchemaInsert extends SchemaStatement {

    private final BatchStatementType batchType;

    public SchemaInsert(Timer timer, StressSettings settings, PartitionGenerator generator, SeedManager seedManager, Distribution batchSize, RatioDistribution useRatio, RatioDistribution rowPopulation, PreparedStatement statement, BatchStatementType batchType) {
        super(timer, settings, new DataSpec(generator, seedManager, batchSize, useRatio, rowPopulation), statement, statement.getColumnNames());
        this.batchType = batchType;
    }

    private class JavaDriverRun extends Runner {
        final JavaDriverClient client;

        private JavaDriverRun(JavaDriverClient client) {
            this.client = client;
        }

        public boolean run() throws Exception {
            List<com.datastax.driver.core.BoundStatement> stmts = new ArrayList<>();
            partitionCount = partitions.size();

            for (PartitionIterator iterator : partitions)
                while (iterator.hasNext())
                    stmts.add(bindRow(iterator.next()).ToV3Value());

            rowCount += stmts.size();

            // 65535 is max number of stmts per batch, so if we have more, we need to manually batch them
            for (int j = 0; j < stmts.size(); j += 65535) {
                List<com.datastax.driver.core.BoundStatement> substmts = stmts.subList(j, Math.min(j + stmts.size(), j + 65535));
                com.datastax.driver.core.Statement stmt;
                if (substmts.size() == 1) {
                    stmt = substmts.get(0);
                } else {
                    com.datastax.driver.core.BatchStatement batch = new com.datastax.driver.core.BatchStatement(batchType.ToV3Value());
                    if (statement.getConsistencyLevel() != null) {
                        batch.setConsistencyLevel(statement.getConsistencyLevel().ToV3Value());
                    }
                    if (statement.getSerialConsistencyLevel() != null) {
                        batch.setSerialConsistencyLevel(statement.getSerialConsistencyLevel().ToV3Value());
                    }
                    batch.addAll(substmts);
                    stmt = batch;
                }

                client.getSession().execute(stmt);
            }
            return true;
        }
    }

    private class JavaDriverV4Run extends Runner {
        final JavaDriverV4Client client;

        private JavaDriverV4Run(JavaDriverV4Client client) {
            this.client = client;
        }

        public boolean run() throws Exception {
            List<com.datastax.oss.driver.api.core.cql.BatchableStatement<?>> stmts = new ArrayList<>();
            partitionCount = partitions.size();

            for (PartitionIterator iterator : partitions)
                while (iterator.hasNext())
                    stmts.add(bindRow(iterator.next()).ToV4Value());

            rowCount += stmts.size();

            // 65535 is max number of stmts per batch, so if we have more, we need to manually batch them
            for (int j = 0; j < stmts.size(); j += 65535) {
                List<? extends com.datastax.oss.driver.api.core.cql.BatchableStatement<?>> substmts = stmts.subList(j, Math.min(j + stmts.size(), j + 65535));
                com.datastax.oss.driver.api.core.cql.Statement stmt;
                if (substmts.size() == 1) {
                    stmt = substmts.get(0);
                } else {
                    com.datastax.oss.driver.api.core.cql.BatchStatementBuilder batch = new com.datastax.oss.driver.api.core.cql.BatchStatementBuilder(batchType.ToV4Value());
                    batch.setConsistencyLevel(statement.getConsistencyLevel().ToV4Value());
                    batch.setSerialConsistencyLevel(statement.getSerialConsistencyLevel().ToV4Value());
                    batch.addStatements((Iterable<BatchableStatement<?>>) substmts);
                    stmt = batch.build();
                }

                client.getSession().execute(stmt);
            }
            return true;
        }
    }

    @Override
    public void run(JavaDriverClient client) throws IOException {
        timeWithRetry(new JavaDriverRun(client));
    }

    @Override
    public void run(JavaDriverV4Client client) throws IOException {
        timeWithRetry(new JavaDriverV4Run(client));
    }

    public boolean isWrite() {
        return true;
    }
}
