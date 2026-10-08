// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v3;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.apache.cassandra.stress.driver.AbstractPreparedStatement;
import org.apache.cassandra.stress.util.ConsistencyLevel;

final class V3PreparedStatement extends AbstractPreparedStatement {
    private final PreparedStatement statement;

    V3PreparedStatement(PreparedStatement statement) {
        super(statement.getQueryString(), columnNames(statement.getVariables()));
        this.statement = statement;
    }

    private static List<String> columnNames(ColumnDefinitions variables) {
        List<String> names = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) {
            names.add(variables.getName(i));
        }
        return names;
    }

    @Override
    public V3BoundStatement bind(Object... values) {
        return new V3BoundStatement(this, values.clone());
    }

    BoundStatement toDriver(Object[] values, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        BoundStatement bound = statement.bind(values);
        ConsistencyLevel level = consistencyOr(consistency);
        ConsistencyLevel serial = serialConsistencyOr(serialConsistency);
        if (level != null) {
            bound.setConsistencyLevel(V3DriverConfig.consistency(level));
        }
        if (serial != null) {
            bound.setSerialConsistencyLevel(V3DriverConfig.consistency(serial));
        }
        return bound;
    }
}
