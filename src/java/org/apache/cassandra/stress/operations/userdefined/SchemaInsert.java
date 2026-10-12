// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.cassandra.stress.driver.BatchType;
import org.apache.cassandra.stress.driver.StressBoundStatement;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressPreparedStatement;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.PartitionIterator;
import org.apache.cassandra.stress.generate.RatioDistribution;
import org.apache.cassandra.stress.generate.Row;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;

public class SchemaInsert extends SchemaStatement {
    static final int MAX_BATCH_SIZE = 65535;

    private final BatchType batchType;
    int maxBatchSize = MAX_BATCH_SIZE;

    public SchemaInsert(
            Timer timer,
            StressSettings settings,
            PartitionGenerator generator,
            SeedManager seedManager,
            Distribution batchSize,
            RatioDistribution useRatio,
            RatioDistribution rowPopulation,
            StressPreparedStatement statement,
            BatchType batchType) {
        super(
                timer,
                settings,
                new DataSpec(generator, seedManager, batchSize, useRatio, rowPopulation),
                statement,
                statement.getColumnNames());
        this.batchType = batchType;
    }

    private final class JavaDriverRun extends Runner {
        final StressClient client;
        private final List<StressBoundStatement> stmts = new ArrayList<>();
        private int partitionIndex;
        private Row pendingRow;
        private boolean allBound;
        private int sentStatements;

        private JavaDriverRun(StressClient client) {
            this.client = client;
        }

        private void bindAll() {
            while (partitionIndex < partitions.size()) {
                PartitionIterator iterator = partitions.get(partitionIndex);
                while (pendingRow != null || iterator.hasNext()) {
                    if (pendingRow == null) {
                        pendingRow = iterator.next();
                    }
                    stmts.add(bindRow(pendingRow));
                    pendingRow = null;
                }
                partitionIndex++;
            }
            allBound = true;
            partitionCount = partitions.size();
            rowCount = stmts.size();
        }

        @Override
        public boolean run() throws Exception {
            if (!allBound) {
                bindAll();
            }
            while (sentStatements < stmts.size()) {
                int end = Math.min(stmts.size(), sentStatements + maxBatchSize);
                client.executeBatch(stmts.subList(sentStatements, end), batchType);
                sentStatements = end;
            }
            return true;
        }
    }

    @Override
    public void run(StressClient client) throws IOException {
        timeWithRetry(new JavaDriverRun(client));
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
