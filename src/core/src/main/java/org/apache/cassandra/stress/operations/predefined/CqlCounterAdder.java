// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.predefined;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.Command;
import org.apache.cassandra.stress.settings.StressSettings;

public class CqlCounterAdder extends CqlOperation<Integer> {

    final Distribution counteradd;

    public CqlCounterAdder(
            DistributionFactory counteradd,
            Timer timer,
            PartitionGenerator generator,
            SeedManager seedManager,
            StressSettings settings) {
        super(Command.COUNTER_WRITE, timer, generator, seedManager, settings);
        this.counteradd = counteradd.get();
    }

    @Override
    protected String buildQuery() {
        StringBuilder query = new StringBuilder("UPDATE counter1 SET ");

        for (int i = 0; i < settings.columns.maxColumnsPerKey; i++) {
            if (i > 0) {
                query.append(',');
            }

            String name = wrapInQuotes(settings.columns.namestrs.get(i));
            query.append(name).append('=').append(name).append("+?");
        }
        query.append(" WHERE KEY=?");
        return query.toString();
    }

    @Override
    protected List<Object> getQueryParameters(byte[] key) {
        final List<Object> list = new ArrayList<>();
        for (int i = 0; i < settings.columns.maxColumnsPerKey; i++) {
            list.add(counteradd.next());
        }
        list.add(ByteBuffer.wrap(key));
        return list;
    }

    @Override
    protected CqlRunOp<Integer> buildRunOp(
            ClientWrapper client, String query, Object queryId, List<Object> params, ByteBuffer key) {
        return new CqlRunOpAlwaysSucceed(client, query, queryId, params, key, 1);
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
