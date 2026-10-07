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
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;

public class SchemaInsert extends SchemaStatement {
    static final int MAX_BATCH_SIZE = 65535;

    private final BatchType batchType;

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
        private List<StressBoundStatement> stmts;

        private JavaDriverRun(StressClient client) {
            this.client = client;
        }

        @Override
        public boolean run() throws Exception {
            if (stmts == null) {
                List<StressBoundStatement> bound = new ArrayList<>();
                for (PartitionIterator iterator : partitions)
                    while (iterator.hasNext()) bound.add(bindRow(iterator.next()));
                stmts = bound;
                partitionCount = partitions.size();
                rowCount = stmts.size();
            }

            for (int j = 0; j < stmts.size(); j += MAX_BATCH_SIZE)
                client.executeBatch(stmts.subList(j, Math.min(stmts.size(), j + MAX_BATCH_SIZE)), batchType);
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
