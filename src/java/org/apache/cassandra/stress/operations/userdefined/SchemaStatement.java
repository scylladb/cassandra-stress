// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

import java.util.List;
import org.apache.cassandra.stress.driver.StressBoundStatement;
import org.apache.cassandra.stress.driver.StressPreparedStatement;
import org.apache.cassandra.stress.generate.Row;
import org.apache.cassandra.stress.operations.PartitionOperation;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;

public abstract class SchemaStatement extends PartitionOperation {
    final StressPreparedStatement statement;
    final int[] argumentIndex;
    final Object[] bindBuffer;
    final boolean printStatementsOnError;

    public SchemaStatement(
            Timer timer,
            StressSettings settings,
            DataSpec spec,
            StressPreparedStatement statement,
            List<String> bindNames) {
        super(timer, settings, spec);
        this.statement = statement;
        argumentIndex = new int[bindNames.size()];
        bindBuffer = new Object[argumentIndex.length];
        int i = 0;
        for (String name : bindNames) argumentIndex[i++] = spec.partitionGenerator.indexOf(name);
        this.printStatementsOnError = settings.log.printStatementsOnError;
    }

    StressBoundStatement bindRow(Row row) {
        assert statement != null;

        for (int i = 0; i < argumentIndex.length; i++) {
            bindBuffer[i] = row.get(argumentIndex[i]);
            if (bindBuffer[i] == null && !spec.partitionGenerator.permitNulls(argumentIndex[i]))
                throw new IllegalStateException();
        }
        return statement.bind(bindBuffer);
    }

    abstract static class Runner implements RunOp {
        int partitionCount;
        int rowCount;

        @Override
        public int partitionCount() {
            return partitionCount;
        }

        @Override
        public int rowCount() {
            return rowCount;
        }
    }

    @Override
    protected String getExceptionMessage(Exception e) {
        String s = super.getExceptionMessage(e);
        if (printStatementsOnError && statement != null) {
            String q = statement.getQueryString();
            s = q + ": " + s;
        }
        return s;
    }
}
