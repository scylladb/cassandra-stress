// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations;

import org.apache.cassandra.stress.Operation;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.commons.math3.distribution.EnumeratedDistribution;

public class SampledOpDistribution implements OpDistribution {

    final EnumeratedDistribution<Operation> operations;
    final Distribution clustering;
    private Operation cur;
    private long remaining;

    public SampledOpDistribution(EnumeratedDistribution<Operation> operations, Distribution clustering) {
        this.operations = operations;
        this.clustering = clustering;
    }

    @Override
    public Operation next() {
        while (remaining == 0) {
            remaining = clustering.next();
            cur = operations.sample();
        }
        remaining--;
        return cur;
    }
}
