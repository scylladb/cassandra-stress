// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.predefined;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.Command;
import org.apache.cassandra.stress.settings.StressSettings;

public class CqlInserter extends CqlOperation<Integer> {

    public CqlInserter(Timer timer, PartitionGenerator generator, SeedManager seedManager, StressSettings settings) {
        super(Command.WRITE, timer, generator, seedManager, settings);
    }

    @Override
    protected String buildQuery() {
        StringBuilder query = new StringBuilder("UPDATE ").append(wrapInQuotes(type.table));
        if (settings.columns.timestamp != null)
            query.append(" USING TIMESTAMP ").append(settings.columns.timestamp);

        query.append(" SET ");

        for (int i = 0; i < settings.columns.maxColumnsPerKey; i++) {
            if (i > 0) query.append(',');

            query.append(wrapInQuotes(settings.columns.namestrs.get(i))).append(" = ?");
        }

        query.append(" WHERE KEY=?");
        return query.toString();
    }

    @Override
    protected List<Object> getQueryParameters(byte[] key) {
        final ArrayList<Object> queryParams = new ArrayList<>();
        List<ByteBuffer> values = getColumnValues();
        queryParams.addAll(values);
        queryParams.add(ByteBuffer.wrap(key));
        return queryParams;
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
