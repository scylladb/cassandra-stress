// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

import java.io.IOException;
import java.util.Random;
import org.apache.cassandra.stress.driver.StressBoundStatement;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressPreparedStatement;
import org.apache.cassandra.stress.generate.DistributionFixed;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.PartitionIterator;
import org.apache.cassandra.stress.generate.Row;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;

public class SchemaQuery extends SchemaStatement {
    public enum ArgSelect {
        MULTIROW,
        SAMEROW;
    }

    final ArgSelect argSelect;
    final Object[][] randomBuffer;
    final Random random = new Random();

    public SchemaQuery(
            Timer timer,
            StressSettings settings,
            PartitionGenerator generator,
            SeedManager seedManager,
            StressPreparedStatement statement,
            ArgSelect argSelect) {
        super(
                timer,
                settings,
                new DataSpec(
                        generator,
                        seedManager,
                        new DistributionFixed(1),
                        settings.insert.rowPopulationRatio.get(),
                        argSelect == ArgSelect.MULTIROW ? statement.variableCount() : 1),
                statement,
                statement.getColumnNames());
        this.argSelect = argSelect;
        randomBuffer = new Object[argumentIndex.length][argumentIndex.length];
    }

    private final class JavaDriverRun extends Runner {
        final StressClient client;
        private StressBoundStatement bound;

        private JavaDriverRun(StressClient client) {
            this.client = client;
        }

        @Override
        public boolean run() throws Exception {
            if (bound == null) {
                bound = bindArgs();
            }
            rowCount = client.executeCount(bound, null, null);
            partitionCount = Math.min(1, rowCount);
            return true;
        }
    }

    private int fillRandom() {
        int c = 0;
        PartitionIterator iterator = partitions.getFirst();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            Object[] randomBufferRow = randomBuffer[c++];
            for (int i = 0; i < argumentIndex.length; i++) {
                randomBufferRow[i] = row.get(argumentIndex[i]);
            }
            if (c >= randomBuffer.length) {
                break;
            }
        }
        assert c > 0;
        return c;
    }

    StressBoundStatement bindArgs() {
        return switch (argSelect) {
            case MULTIROW -> {
                int c = fillRandom();
                for (int i = 0; i < argumentIndex.length; i++) {
                    int argIndex = argumentIndex[i];
                    bindBuffer[i] = randomBuffer[argIndex < 0 ? 0 : random.nextInt(c)][i];
                }
                yield statement.bind(bindBuffer);
            }
            case SAMEROW -> bindRow(partitions.getFirst().next());
        };
    }

    @Override
    public void run(StressClient client) throws IOException {
        timeWithRetry(new JavaDriverRun(client));
    }
}
