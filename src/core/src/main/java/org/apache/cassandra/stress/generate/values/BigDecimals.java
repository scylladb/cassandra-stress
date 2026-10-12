// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.math.BigDecimal;
import org.apache.cassandra.stress.marshal.DecimalType;

public class BigDecimals extends Generator<BigDecimal> {
    public BigDecimals(String name, GeneratorConfig config) {
        super(DecimalType.instance, config, name, BigDecimal.class);
    }

    @Override
    public BigDecimal generate() {
        return BigDecimal.valueOf(identityDistribution.next());
    }
}
