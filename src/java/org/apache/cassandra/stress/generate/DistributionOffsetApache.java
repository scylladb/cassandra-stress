// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import org.apache.commons.math3.distribution.AbstractRealDistribution;

public class DistributionOffsetApache extends Distribution
{

    final AbstractRealDistribution delegate;
    final long min, delta;

    public DistributionOffsetApache(AbstractRealDistribution delegate, long min, long max)
    {
        this.delegate = delegate;
        this.min = min;
        this.delta = max - min;
    }

    public void setSeed(long seed)
    {
        delegate.reseedRandomGenerator(seed);
    }

    @Override
    public long next()
    {
        return offset(min, delta, delegate.sample());
    }

    public double nextDouble()
    {
        return offsetDouble(min, delta, delegate.sample());
    }

    @Override
    public long inverseCumProb(double cumProb)
    {
        return offset(min, delta, delegate.inverseCumulativeProbability(cumProb));
    }

    private long offset(long min, long delta, double val)
    {
        long r = (long) val;
        if (r < 0)
            r = 0;
        if (r > delta)
            r = delta;
        return min + r;
    }

    private double offsetDouble(long min, long delta, double r)
    {
        if (r < 0)
            r = 0;
        if (r > delta)
            r = delta;
        return min + r;
    }

}
