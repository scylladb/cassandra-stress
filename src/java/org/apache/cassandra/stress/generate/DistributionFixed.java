// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

public class DistributionFixed extends Distribution {

    final long key;

    public DistributionFixed(long key) {
        this.key = key;
    }

    @Override
    public long next() {
        return key;
    }

    @Override
    public double nextDouble() {
        return key;
    }

    @Override
    public long inverseCumProb(double cumProb) {
        return key;
    }

    @Override
    public void setSeed(long seed) {}
}
