// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

public class RatioDistribution
{

    final Distribution distribution;
    final double divisor;

    public RatioDistribution(Distribution distribution, double divisor)
    {
        this.distribution = distribution;
        this.divisor = divisor;
    }

    public double next()
    {
        return Math.clamp(distribution.nextDouble() / divisor, 0d, 1d);
    }

    public double min()
    {
        return Math.min(1d, distribution.minValue() / divisor);
    }

    public double max()
    {
        return Math.min(1d, distribution.maxValue() / divisor);
    }
}
