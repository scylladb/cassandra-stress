// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.math.BigInteger;
import org.apache.cassandra.stress.marshal.IntegerType;

public class BigIntegers extends Generator<BigInteger> {
    public BigIntegers(String name, GeneratorConfig config) {
        super(IntegerType.instance, config, name, BigInteger.class);
    }

    @Override
    public BigInteger generate() {
        return BigInteger.valueOf(identityDistribution.next());
    }
}
