// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.Int32Type;

public class Integers extends Generator<Integer> {

    public Integers(String name, GeneratorConfig config) {
        super(Int32Type.instance, config, name, Integer.class);
    }

    @Override
    public Integer generate() {
        return (int) identityDistribution.next();
    }
}
