// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import org.apache.commons.math3.distribution.AbstractRealDistribution;

public class DistributionBoundApache extends Distribution
{

    final AbstractRealDistribution delegate;
    final long min, max;

    public DistributionBoundApache(AbstractRealDistribution delegate, long min, long max)
    {
        this.delegate = delegate;
        this.min = min;
        this.max = max;
    }

    @Override
    public long next()
    {
        return bound(min, max, delegate.sample());
    }

    public double nextDouble()
    {
        return boundDouble(min, max, delegate.sample());
    }

    @Override
    public long inverseCumProb(double cumProb)
    {
        return bound(min, max, delegate.inverseCumulativeProbability(cumProb));
    }

    public void setSeed(long seed)
    {
        delegate.reseedRandomGenerator(seed);
    }

    private static long bound(long min, long max, double val)
    {
        long r = (long) val;
        if ((r >= min) && (r <= max))
            return r;
        if (r < min)
            return min;
        if (r > max)
            return max;
        throw new IllegalStateException();
    }

    private static double boundDouble(long min, long max, double r)
    {
        if ((r >= min) && (r <= max))
            return r;
        if (r < min)
            return min;
        if (r > max)
            return max;
        throw new IllegalStateException();
    }

}
