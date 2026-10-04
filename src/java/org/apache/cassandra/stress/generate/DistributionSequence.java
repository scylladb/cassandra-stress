// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import java.util.concurrent.atomic.AtomicLong;

public class DistributionSequence extends Distribution
{

    private final long start;
    private final long totalCount;
    private final AtomicLong next = new AtomicLong();

    public DistributionSequence(long start, long end)
    {
        if (start > end)
            throw new IllegalStateException();
        this.start = start;
        this.totalCount = 1 + end - start;
    }

    private long nextWithWrap()
    {
        long next = this.next.getAndIncrement();
        return start + (next % totalCount);
    }

    @Override
    public long next()
    {
        return nextWithWrap();
    }

    @Override
    public double nextDouble()
    {
        return nextWithWrap();
    }

    @Override
    public long inverseCumProb(double cumProb)
    {
        return (long) (start + (totalCount-1) * cumProb);
    }

    @Override
    public void setSeed(long seed)
    {
        next.set(seed);
    }

}

