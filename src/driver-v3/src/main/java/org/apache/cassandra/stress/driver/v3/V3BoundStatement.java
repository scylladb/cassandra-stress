// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v3;

import com.datastax.driver.core.BoundStatement;
import org.apache.cassandra.stress.driver.StressBoundStatement;
import org.apache.cassandra.stress.util.ConsistencyLevel;

@SuppressWarnings("ArrayRecordComponent")
record V3BoundStatement(V3PreparedStatement statement, Object[] values) implements StressBoundStatement {
    BoundStatement toDriver(ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        return statement.toDriver(values, consistency, serialConsistency);
    }
}
