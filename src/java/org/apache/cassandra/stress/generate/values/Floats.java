// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.FloatType;

public class Floats extends Generator<Float> {
    public Floats(String name, GeneratorConfig config) {
        super(FloatType.instance, config, name, Float.class);
    }

    @Override
    public Float generate() {
        return (float) identityDistribution.nextDouble();
    }
}
