// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import java.io.IOException;
import java.util.Random;
import org.apache.cassandra.stress.core.PreparedStatement;
import org.apache.cassandra.stress.generate.DistributionFixed;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.PartitionIterator;
import org.apache.cassandra.stress.generate.Row;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.JavaDriverClient;

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
            PreparedStatement statement,
            ArgSelect argSelect) {
        super(
                timer,
                settings,
                new DataSpec(
                        generator,
                        seedManager,
                        new DistributionFixed(1),
                        settings.insert.rowPopulationRatio.get(),
                        argSelect == ArgSelect.MULTIROW
                                ? statement.getVariables().size()
                                : 1),
                statement,
                statement.getColumnNames());
        this.argSelect = argSelect;
        randomBuffer = new Object[argumentIndex.length][argumentIndex.length];
    }

    private final class JavaDriverRun extends Runner {
        final JavaDriverClient client;
        private BoundStatement bound;

        private JavaDriverRun(JavaDriverClient client) {
            this.client = client;
        }

        @Override
        public boolean run() throws Exception {
            if (bound == null) bound = bindArgs();
            ResultSet rs = client.getSession().execute(bound);
            rowCount = rs.all().size();
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
            for (int i = 0; i < argumentIndex.length; i++) randomBufferRow[i] = row.get(argumentIndex[i]);
            if (c >= randomBuffer.length) break;
        }
        assert c > 0;
        return c;
    }

    BoundStatement bindArgs() {
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
    public void run(JavaDriverClient client) throws IOException {
        timeWithRetry(new JavaDriverRun(client));
    }
}
