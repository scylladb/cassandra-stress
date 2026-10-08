// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

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
}
