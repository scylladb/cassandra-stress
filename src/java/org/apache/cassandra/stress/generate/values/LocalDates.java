// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.SimpleDateType;

public class LocalDates extends Generator<Integer>
{

    public LocalDates(String name, GeneratorConfig config)
    {
        super(SimpleDateType.instance, config, name, Integer.class);
    }

    public Integer generate()
    {
        return (int)identityDistribution.next();
    }

}
