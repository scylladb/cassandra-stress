// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.apache.commons.math3.random.RandomGenerator;

public class DistributionOffsetApache extends Distribution {

    final AbstractRealDistribution delegate;
    final RandomGenerator random;
    final long min;
    final long delta;

    /** {@code random} is the generator that {@code delegate} samples from. */
    public DistributionOffsetApache(RandomGenerator random, AbstractRealDistribution delegate, long min, long max) {
        this.delegate = delegate;
        this.random = random;
        this.min = min;
        this.delta = max - min;
    }

    @Override
    public void setSeed(long seed) {
        // Seeds the generator of sample() only. AbstractRealDistribution.reseedRandomGenerator also creates
        // a Well19937c of about 15 KiB, once for each distribution of each stress thread.
        random.setSeed(seed);
    }

    @Override
    public long next() {
        return offset(min, delta, delegate.sample());
    }

    @Override
    public double nextDouble() {
        return offsetDouble(min, delta, delegate.sample());
    }

    @Override
    public long inverseCumProb(double cumProb) {
        return offset(min, delta, delegate.inverseCumulativeProbability(cumProb));
    }

    private long offset(long min, long delta, double val) {
        long r = (long) val;
        if (r < 0) {
            r = 0;
        }
        if (r > delta) {
            r = delta;
        }
        return min + r;
    }

    private double offsetDouble(long min, long delta, double r) {
        if (r < 0) {
            r = 0;
        }
        if (r > delta) {
            r = delta;
        }
        return min + r;
    }
}
