// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.ShortType;

public class SmallInts extends Generator<Short>
{
    public SmallInts(String name, GeneratorConfig config)
    {
        super(ShortType.instance, config, name, Short.class);
    }

    public Short generate()
    {
        long seed = identityDistribution.next();
        return (short)seed;
    }
}
