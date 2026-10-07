// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v4;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.apache.cassandra.stress.driver.AbstractPreparedStatement;
import org.apache.cassandra.stress.util.ConsistencyLevel;

final class V4PreparedStatement extends AbstractPreparedStatement {
    private final PreparedStatement statement;

    V4PreparedStatement(PreparedStatement statement) {
        super(statement.getQuery(), columnNames(statement));
        this.statement = statement;
    }

    private static List<String> columnNames(PreparedStatement statement) {
        List<String> names = new ArrayList<>();
        for (ColumnDefinition definition : statement.getVariableDefinitions())
            names.add(definition.getName().asInternal());
        return names;
    }

    @Override
    public V4BoundStatement bind(Object... values) {
        return new V4BoundStatement(this, values.clone());
    }

    BoundStatement toDriver(Object[] values, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        BoundStatementBuilder builder = statement.boundStatementBuilder(values);
        ConsistencyLevel level = consistencyOr(consistency);
        ConsistencyLevel serial = serialConsistencyOr(serialConsistency);
        if (level != null) builder.setConsistencyLevel(V4DriverConfig.consistency(level));
        if (serial != null) builder.setSerialConsistencyLevel(V4DriverConfig.consistency(serial));
        return builder.build();
    }
}
