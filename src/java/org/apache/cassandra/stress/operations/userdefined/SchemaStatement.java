// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.userdefined;

import java.util.List;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.LocalDate;
import org.apache.cassandra.stress.core.BoundStatement;
import org.apache.cassandra.stress.core.ColumnDefinitions;
import org.apache.cassandra.stress.core.PreparedStatement;
import org.apache.cassandra.stress.generate.Row;
import org.apache.cassandra.stress.operations.PartitionOperation;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;

public abstract class SchemaStatement extends PartitionOperation
{
    final PreparedStatement statement;
    final int[] argumentIndex;
    final Object[] bindBuffer;
    final ColumnDefinitions definitions;
    final boolean printStatementsOnError;
    static final DataType.Name v3DateTypeName = DataType.date().getName();

    public SchemaStatement(Timer timer, StressSettings settings, DataSpec spec,
                           PreparedStatement statement, List<String> bindNames)
    {
        super(timer, settings, spec);
        this.statement = statement;
        argumentIndex = new int[bindNames.size()];
        bindBuffer = new Object[argumentIndex.length];
        definitions = statement != null ? statement.getVariables() : null;
        int i = 0;
        for (String name : bindNames)
            argumentIndex[i++] = spec.partitionGenerator.indexOf(name);
        this.printStatementsOnError = settings.log.printStatementsOnError;
    }

    BoundStatement bindRow(Row row)
    {
        assert statement != null;

        for (int i = 0 ; i < argumentIndex.length ; i++)
        {
            Object value = row.get(argumentIndex[i]);
            if (definitions.isDateType(i))
            {
                value= LocalDate.fromDaysSinceEpoch((Integer) value);
            }
            bindBuffer[i] = value;
            if (bindBuffer[i] == null && !spec.partitionGenerator.permitNulls(argumentIndex[i]))
                throw new IllegalStateException();
        }
        return statement.bind(bindBuffer);
    }

    abstract class Runner implements RunOp
    {
        int partitionCount;
        int rowCount;

        @Override
        public int partitionCount()
        {
            return partitionCount;
        }

        @Override
        public int rowCount()
        {
            return rowCount;
        }
    }

    @Override
    protected String getExceptionMessage(Exception e) {
        String s = super.getExceptionMessage(e);
        if (printStatementsOnError && statement != null) {
            String q = statement.getQueryString();
            s = String.valueOf(q) + ": " + s;
        }
        return s;
    }
}
