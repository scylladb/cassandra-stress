// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.DecimalType;

import java.math.BigDecimal;

public class BigDecimals extends Generator<BigDecimal>
{
    public BigDecimals(String name, GeneratorConfig config)
    {
        super(DecimalType.instance, config, name, BigDecimal.class);
    }

    @Override
    public BigDecimal generate()
    {
        return BigDecimal.valueOf(identityDistribution.next());
    }
}
