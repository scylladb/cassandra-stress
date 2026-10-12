// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.io.IOException;
import java.util.List;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.util.ConsistencyLevel;

public interface StressClient {
    void connect(ProtocolCompression compression) throws IOException;

    void execute(String query, ConsistencyLevel consistency);

    StressPreparedStatement prepare(String query);

    StressResult execute(String query, ConsistencyLevel consistency, ConsistencyLevel serialConsistency);

    StressResult execute(
            StressBoundStatement statement, ConsistencyLevel consistency, ConsistencyLevel serialConsistency);

    int executeCount(String query, ConsistencyLevel consistency, ConsistencyLevel serialConsistency);

    int executeCount(StressBoundStatement statement, ConsistencyLevel consistency, ConsistencyLevel serialConsistency);

    void executeBatch(List<StressBoundStatement> statements, BatchType type);

    StressPage executePage(String query, int pageSize, Object pagingState);

    TableSchema tableSchema(String keyspace, String table);

    List<TokenSlice> tokenRanges();

    void disconnect();
}
