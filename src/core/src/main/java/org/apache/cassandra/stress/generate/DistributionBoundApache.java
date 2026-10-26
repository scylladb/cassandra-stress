// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.apache.commons.math3.random.RandomGenerator;

public class DistributionBoundApache extends Distribution {

    final AbstractRealDistribution delegate;
    final RandomGenerator random;
    final long min;
    final long max;

    /** {@code random} is the generator that {@code delegate} samples from. */
    public DistributionBoundApache(RandomGenerator random, AbstractRealDistribution delegate, long min, long max) {
        this.delegate = delegate;
        this.random = random;
        this.min = min;
        this.max = max;
    }

    @Override
    public long next() {
        return bound(min, max, delegate.sample());
    }

    @Override
    public double nextDouble() {
        return boundDouble(min, max, delegate.sample());
    }

    @Override
    public long inverseCumProb(double cumProb) {
        return bound(min, max, delegate.inverseCumulativeProbability(cumProb));
    }

    @Override
    public void setSeed(long seed) {
        // Seeds the generator of sample() only. AbstractRealDistribution.reseedRandomGenerator also creates
        // a Well19937c of about 15 KiB, once for each distribution of each stress thread.
        random.setSeed(seed);
    }

    private static long bound(long min, long max, double val) {
        long r = (long) val;
        if ((r >= min) && (r <= max)) {
            return r;
        }
        if (r < min) {
            return min;
        }
        if (r > max) {
            return max;
        }
        throw new IllegalStateException();
    }

    private static double boundDouble(long min, long max, double r) {
        if ((r >= min) && (r <= max)) {
            return r;
        }
        if (r < min) {
            return min;
        }
        if (r > max) {
            return max;
        }
        throw new IllegalStateException();
    }
}
