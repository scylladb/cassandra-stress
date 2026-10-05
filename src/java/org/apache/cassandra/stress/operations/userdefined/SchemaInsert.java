// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

import com.datastax.oss.driver.api.core.cql.BatchableStatement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.cassandra.stress.core.BatchStatementType;
import org.apache.cassandra.stress.core.PreparedStatement;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.PartitionIterator;
import org.apache.cassandra.stress.generate.RatioDistribution;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.JavaDriverClient;
import org.apache.cassandra.stress.util.JavaDriverV4Client;

public class SchemaInsert extends SchemaStatement {

    private final BatchStatementType batchType;

    public SchemaInsert(
            Timer timer,
            StressSettings settings,
            PartitionGenerator generator,
            SeedManager seedManager,
            Distribution batchSize,
            RatioDistribution useRatio,
            RatioDistribution rowPopulation,
            PreparedStatement statement,
            BatchStatementType batchType) {
        super(
                timer,
                settings,
                new DataSpec(generator, seedManager, batchSize, useRatio, rowPopulation),
                statement,
                statement.getColumnNames());
        this.batchType = batchType;
    }

    private final class JavaDriverRun extends Runner {
        final JavaDriverClient client;

        private JavaDriverRun(JavaDriverClient client) {
            this.client = client;
        }

        @Override
        public boolean run() throws Exception {
            List<com.datastax.driver.core.BoundStatement> stmts = new ArrayList<>();
            partitionCount = partitions.size();

            for (PartitionIterator iterator : partitions)
                while (iterator.hasNext()) stmts.add(bindRow(iterator.next()).toV3Value());

            rowCount += stmts.size();

            for (int j = 0; j < stmts.size(); j += 65535) {
                List<com.datastax.driver.core.BoundStatement> substmts =
                        stmts.subList(j, Math.min(j + stmts.size(), j + 65535));
                com.datastax.driver.core.Statement stmt;
                if (substmts.size() == 1) {
                    stmt = substmts.getFirst();
                } else {
                    com.datastax.driver.core.BatchStatement batch =
                            new com.datastax.driver.core.BatchStatement(batchType.toV3Value());
                    if (statement.getConsistencyLevel() != null) {
                        batch.setConsistencyLevel(
                                statement.getConsistencyLevel().toV3Value());
                    }
                    if (statement.getSerialConsistencyLevel() != null) {
                        batch.setSerialConsistencyLevel(
                                statement.getSerialConsistencyLevel().toV3Value());
                    }
                    batch.addAll(substmts);
                    stmt = batch;
                }

                client.getSession().execute(stmt);
            }
            return true;
        }
    }

    private final class JavaDriverV4Run extends Runner {
        final JavaDriverV4Client client;

        private JavaDriverV4Run(JavaDriverV4Client client) {
            this.client = client;
        }

        @Override
        public boolean run() throws Exception {
            List<com.datastax.oss.driver.api.core.cql.BatchableStatement<?>> stmts = new ArrayList<>();
            partitionCount = partitions.size();

            for (PartitionIterator iterator : partitions)
                while (iterator.hasNext()) stmts.add(bindRow(iterator.next()).toV4Value());

            rowCount += stmts.size();

            for (int j = 0; j < stmts.size(); j += 65535) {
                List<? extends com.datastax.oss.driver.api.core.cql.BatchableStatement<?>> substmts =
                        stmts.subList(j, Math.min(j + stmts.size(), j + 65535));
                com.datastax.oss.driver.api.core.cql.Statement stmt;
                if (substmts.size() == 1) {
                    stmt = substmts.getFirst();
                } else {
                    com.datastax.oss.driver.api.core.cql.BatchStatementBuilder batch =
                            new com.datastax.oss.driver.api.core.cql.BatchStatementBuilder(batchType.toV4Value());
                    batch.setConsistencyLevel(statement.getConsistencyLevel().toV4Value());
                    batch.setSerialConsistencyLevel(
                            statement.getSerialConsistencyLevel().toV4Value());
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

    @Override
    public boolean isWrite() {
        return true;
    }
}
