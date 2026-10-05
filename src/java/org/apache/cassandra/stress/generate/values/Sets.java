// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.util.HashSet;
import java.util.Set;
import org.apache.cassandra.stress.marshal.SetType;

public class Sets<T> extends Generator<Set<T>> {
    final Generator<T> valueType;

    public Sets(String name, Generator<T> valueType, GeneratorConfig config) {
        super(SetType.getInstance(valueType.type, true), config, name, Set.class);
        this.valueType = valueType;
    }

    @Override
    public void setSeed(long seed) {
        super.setSeed(seed);
        valueType.setSeed(seed * 31);
    }

    @Override
    public Set<T> generate() {
        final Set<T> set = new HashSet<T>();
        int size = (int) sizeDistribution.next();
        for (int i = 0; i < size; i++) set.add(valueType.generate());
        return set;
    }
}
