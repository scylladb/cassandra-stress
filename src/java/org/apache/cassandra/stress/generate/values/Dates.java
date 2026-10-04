// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.util.Date;

import org.apache.cassandra.stress.marshal.DateType;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.stress.settings.OptionDistribution;

public class Dates extends Generator<Date>
{
    public Dates(String name, GeneratorConfig config)
    {
        super(DateType.instance, config, name, Date.class);
    }

    @Override
    public Date generate()
    {
        return new Date(identityDistribution.next());
    }

    DistributionFactory defaultIdentityDistribution()
    {
        return OptionDistribution.get("uniform(1.." + Long.toString(50L*365L*24L*60L*60L*1000L) + ")");
    }
}
