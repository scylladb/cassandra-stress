// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

public class DistributionInverted extends Distribution {

    final Distribution wrapped;
    final long min;
    final long max;

    public DistributionInverted(Distribution wrapped) {
        this.wrapped = wrapped;
        this.min = wrapped.minValue();
        this.max = wrapped.maxValue();
    }

    @Override
    public long next() {
        return max - (wrapped.next() - min);
    }

    @Override
    public double nextDouble() {
        return max - (wrapped.nextDouble() - min);
    }

    @Override
    public long inverseCumProb(double cumProb) {
        return max - (wrapped.inverseCumProb(cumProb) - min);
    }

    @Override
    public void setSeed(long seed) {
        wrapped.setSeed(seed);
    }

    public static Distribution invert(Distribution distribution) {
        if (distribution instanceof DistributionInverted inverted) return inverted.wrapped;
        return new DistributionInverted(distribution);
    }
}
