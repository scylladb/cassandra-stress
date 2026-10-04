// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations;

import org.apache.cassandra.stress.StressAction.MeasurementSink;

public interface OpDistributionFactory
{
    public OpDistribution get(boolean isWarmup, MeasurementSink sink);
    public String desc();
    Iterable<OpDistributionFactory> each();
}
