// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.predefined;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.Command;
import org.apache.cassandra.stress.settings.StressSettings;

public class CqlCounterGetter extends CqlOperation<Integer> {

    public CqlCounterGetter(
            Timer timer, PartitionGenerator generator, SeedManager seedManager, StressSettings settings) {
        super(Command.COUNTER_READ, timer, generator, seedManager, settings);
    }

    @Override
    protected List<Object> getQueryParameters(byte[] key) {
        return Collections.<Object>singletonList(ByteBuffer.wrap(key));
    }

    @Override
    protected String buildQuery() {
        return "SELECT * FROM " + wrapInQuotes(type.table) + " WHERE KEY=?";
    }

    @Override
    protected CqlRunOp<Integer> buildRunOp(
            ClientWrapper client, String query, Object queryId, List<Object> params, ByteBuffer key) {
        return new CqlRunOpTestNonEmpty(client, query, queryId, params, key);
    }
}
