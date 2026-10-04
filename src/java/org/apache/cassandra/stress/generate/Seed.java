// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.cassandra.stress.util.DynamicList;

public class Seed implements Comparable<Seed>
{

    public final int visits;
    public final long seed;

    private volatile DynamicList.Node poolNode;
    private volatile int position;

    private static final AtomicIntegerFieldUpdater<Seed> positionUpdater = AtomicIntegerFieldUpdater.newUpdater(Seed.class, "position");

    public int compareTo(Seed that)
    {
        return Long.compare(this.seed, that.seed);
    }

    Seed(long seed, int visits)
    {
        this.seed = seed;
        this.visits = visits;
    }

    public int position()
    {
        return position;
    }

    public int moveForwards(int rowCount)
    {
        return positionUpdater.getAndAdd(this, rowCount);
    }

    public int hashCode()
    {
        return (int) seed;
    }

    public boolean equals(Object that)
    {
        return that instanceof Seed other && this.seed == other.seed;
    }

    public boolean save(DynamicList<Seed> sampleFrom, int maxSize)
    {
        DynamicList.Node poolNode = sampleFrom.append(this, maxSize);
        if (poolNode == null)
            return false;
        this.poolNode = poolNode;
        return true;
    }

    public boolean isSaved()
    {
        return poolNode != null;
    }

    public void remove(DynamicList<Seed> sampleFrom)
    {
        sampleFrom.remove(poolNode);
    }
}
