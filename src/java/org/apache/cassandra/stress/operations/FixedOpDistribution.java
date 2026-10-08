// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations;

import org.apache.cassandra.stress.Operation;

public class FixedOpDistribution implements OpDistribution {
    final Operation operation;

    public FixedOpDistribution(Operation operation) {
        this.operation = operation;
    }

    @Override
    public Operation next() {
        return operation;
    }
}
