// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.LongType;

public class Longs extends Generator<Long>
{
    public Longs(String name, GeneratorConfig config)
    {
        super(LongType.instance, config, name, Long.class);
    }

    @Override
    public Long generate()
    {
        return identityDistribution.next();
    }
}
