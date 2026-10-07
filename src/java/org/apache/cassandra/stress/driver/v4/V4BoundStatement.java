// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v4;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import org.apache.cassandra.stress.driver.StressBoundStatement;
import org.apache.cassandra.stress.util.ConsistencyLevel;

@SuppressWarnings("ArrayRecordComponent")
record V4BoundStatement(V4PreparedStatement statement, Object[] values) implements StressBoundStatement {
    BoundStatement toDriver(ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        return statement.toDriver(values, consistency, serialConsistency);
    }
}
