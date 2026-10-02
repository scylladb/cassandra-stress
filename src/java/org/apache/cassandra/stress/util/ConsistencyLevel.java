// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

public enum ConsistencyLevel
{
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

    ConsistencyLevel(boolean isDCLocal)
    {
        this.isDCLocal = isDCLocal;
    }

    public boolean isDatacenterLocal()
    {
        return isDCLocal;
    }

    public boolean isSerialConsistency()
    {
        return this == SERIAL || this == LOCAL_SERIAL;
    }

    public com.datastax.driver.core.ConsistencyLevel ToV3Value()
    {
        switch (this)
        {
            case ANY:
                return com.datastax.driver.core.ConsistencyLevel.ANY;
            case ONE:
                return com.datastax.driver.core.ConsistencyLevel.ONE;
            case TWO:
                return com.datastax.driver.core.ConsistencyLevel.TWO;
            case THREE:
                return com.datastax.driver.core.ConsistencyLevel.THREE;
            case QUORUM:
                return com.datastax.driver.core.ConsistencyLevel.QUORUM;
            case ALL:
                return com.datastax.driver.core.ConsistencyLevel.ALL;
            case LOCAL_QUORUM:
                return com.datastax.driver.core.ConsistencyLevel.LOCAL_QUORUM;
            case EACH_QUORUM:
                return com.datastax.driver.core.ConsistencyLevel.EACH_QUORUM;
            case SERIAL:
                return com.datastax.driver.core.ConsistencyLevel.SERIAL;
            case LOCAL_SERIAL:
                return com.datastax.driver.core.ConsistencyLevel.LOCAL_SERIAL;
            case LOCAL_ONE:
                return com.datastax.driver.core.ConsistencyLevel.LOCAL_ONE;
        }
        throw new AssertionError();
    }

    public shaded.com.datastax.oss.driver.api.core.ConsistencyLevel ToV4Value()
    {
        switch (this)
        {
            case ANY:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.ANY;
            case ONE:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.ONE;
            case TWO:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.TWO;
            case THREE:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.THREE;
            case QUORUM:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.QUORUM;
            case ALL:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.ALL;
            case LOCAL_QUORUM:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.LOCAL_QUORUM;
            case EACH_QUORUM:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.EACH_QUORUM;
            case SERIAL:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.SERIAL;
            case LOCAL_SERIAL:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.LOCAL_SERIAL;
            case LOCAL_ONE:
                return shaded.com.datastax.oss.driver.api.core.ConsistencyLevel.LOCAL_ONE;
        }
        throw new AssertionError();
    }
}
