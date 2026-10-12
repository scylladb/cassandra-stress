// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.ByteType;

public class TinyInts extends Generator<Byte> {
    public TinyInts(String name, GeneratorConfig config) {
        super(ByteType.instance, config, name, Byte.class);
    }

    @Override
    public Byte generate() {
        long seed = identityDistribution.next();
        return (byte) seed;
    }
}
