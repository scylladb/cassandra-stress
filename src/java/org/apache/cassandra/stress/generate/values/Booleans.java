// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.BooleanType;

public class Booleans extends Generator<Boolean>
{
    public Booleans(String name, GeneratorConfig config)
    {
        super(BooleanType.instance, config, name, Boolean.class);
    }

    @Override
    public Boolean generate()
    {
        return identityDistribution.next() % 1 == 0;
    }
}
