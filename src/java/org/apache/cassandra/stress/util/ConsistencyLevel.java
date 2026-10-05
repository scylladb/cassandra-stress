// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;

public enum ConsistencyLevel {
    ANY(false),
    ONE(false),
    TWO(false),
    THREE(false),
    QUORUM(false),
    ALL(false),
    LOCAL_QUORUM(true),
    EACH_QUORUM(false),
    SERIAL(false),
    LOCAL_SERIAL(false),
    LOCAL_ONE(true);

    private final boolean isDCLocal;

    ConsistencyLevel(boolean isDCLocal) {
        this.isDCLocal = isDCLocal;
    }

    public boolean isDatacenterLocal() {
        return isDCLocal;
    }

    public boolean isSerialConsistency() {
        return this == SERIAL || this == LOCAL_SERIAL;
    }

    public com.datastax.driver.core.ConsistencyLevel toV3Value() {
        return com.datastax.driver.core.ConsistencyLevel.valueOf(name());
    }

    public com.datastax.oss.driver.api.core.ConsistencyLevel toV4Value() {
        return DefaultConsistencyLevel.valueOf(name());
    }
}
