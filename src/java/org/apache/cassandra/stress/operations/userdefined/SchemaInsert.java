// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchableStatement;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.Statement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.cassandra.stress.core.PreparedStatement;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.PartitionIterator;
import org.apache.cassandra.stress.generate.RatioDistribution;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.JavaDriverClient;

public class SchemaInsert extends SchemaStatement {
    static final int MAX_BATCH_SIZE = 65535;

    private final DefaultBatchType batchType;

    public SchemaInsert(
            Timer timer,
            StressSettings settings,
            PartitionGenerator generator,
            SeedManager seedManager,
            Distribution batchSize,
            RatioDistribution useRatio,
            RatioDistribution rowPopulation,
            PreparedStatement statement,
            DefaultBatchType batchType) {
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
        private List<BatchableStatement<?>> stmts;

        private JavaDriverRun(JavaDriverClient client) {
            this.client = client;
        }

        @Override
        public boolean run() throws Exception {
            if (stmts == null) {
                List<BatchableStatement<?>> bound = new ArrayList<>();
                for (PartitionIterator iterator : partitions)
                    while (iterator.hasNext()) bound.add(bindRow(iterator.next()));
                stmts = bound;
                partitionCount = partitions.size();
                rowCount = stmts.size();
            }

            for (int j = 0; j < stmts.size(); j += MAX_BATCH_SIZE)
                client.getSession().execute(statement(stmts.subList(j, Math.min(stmts.size(), j + MAX_BATCH_SIZE))));
            return true;
        }
    }

    Statement<?> statement(List<BatchableStatement<?>> stmts) {
        if (stmts.size() == 1) return stmts.getFirst();
        BatchStatementBuilder batch = new BatchStatementBuilder(batchType);
        if (statement.getConsistencyLevel() != null)
            batch.setConsistencyLevel(statement.getConsistencyLevel().toDriver());
        if (statement.getSerialConsistencyLevel() != null)
            batch.setSerialConsistencyLevel(
                    statement.getSerialConsistencyLevel().toDriver());
        return batch.addStatements(stmts).build();
    }

    @Override
    public void run(JavaDriverClient client) throws IOException {
        timeWithRetry(new JavaDriverRun(client));
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
