// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.predefined;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.Command;
import org.apache.cassandra.stress.settings.StressSettings;

public class CqlReader extends CqlOperation<ByteBuffer[][]>
{

    public CqlReader(Timer timer, PartitionGenerator generator, SeedManager seedManager, StressSettings settings)
    {
        super(Command.READ, timer, generator, seedManager, settings);
    }

    @Override
    protected String buildQuery()
    {
        StringBuilder query = new StringBuilder("SELECT ");

        if (settings.columns.slice)
        {
            query.append("*");
        }
        else
        {
            for (int i = 0; i < settings.columns.maxColumnsPerKey ; i++)
            {
                if (i > 0)
                    query.append(",");
                query.append(wrapInQuotes(settings.columns.namestrs.get(i)));
            }
        }

        query.append(" FROM ").append(wrapInQuotes(type.table));
        query.append(" WHERE KEY=?");
        return query.toString();
    }

    @Override
    protected List<Object> getQueryParameters(byte[] key)
    {
        return Collections.<Object>singletonList(ByteBuffer.wrap(key));
    }

    @Override
    protected CqlRunOp<ByteBuffer[][]> buildRunOp(ClientWrapper client, String query, Object queryId, List<Object> params, ByteBuffer key)
    {
        List<ByteBuffer> expectRow = getColumnValues();
        return new CqlRunOpMatchResults(client, query, queryId, params, key, Arrays.asList(expectRow));
    }

}
