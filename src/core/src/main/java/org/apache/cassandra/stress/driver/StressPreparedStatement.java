// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.util.List;
import org.apache.cassandra.stress.util.ConsistencyLevel;

public interface StressPreparedStatement {
    String getQueryString();

    List<String> getColumnNames();

    default int variableCount() {
        return getColumnNames().size();
    }

    ConsistencyLevel getConsistencyLevel();

    void setConsistencyLevel(ConsistencyLevel level);

    ConsistencyLevel getSerialConsistencyLevel();

    void setSerialConsistencyLevel(ConsistencyLevel level);

    StressBoundStatement bind(Object... values);
}
