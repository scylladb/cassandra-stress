// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.DoubleType;

public class Doubles extends Generator<Double>
{
    public Doubles(String name, GeneratorConfig config)
    {
        super(DoubleType.instance, config, name, Double.class);
    }

    @Override
    public Double generate()
    {
        return identityDistribution.nextDouble();
    }
}
