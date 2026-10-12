// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

public abstract class Distribution {

    public abstract long next();

    public abstract double nextDouble();

    public abstract long inverseCumProb(double cumProb);

    public abstract void setSeed(long seed);

    public long maxValue() {
        return inverseCumProb(1d);
    }

    public long minValue() {
        return inverseCumProb(0d);
    }

    @SuppressWarnings("PMD.DontUseFloatTypeForLoopIndices")
    public long average() {
        double sum = 0;
        int count = 0;
        for (float d = 0; d <= 1.0d; d = (float) (d + 0.02d)) {
            sum += inverseCumProb(d);
            count += 1;
        }
        return (long) (sum / count);
    }
}
