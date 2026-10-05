// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.cassandra.stress.marshal.ListType;

public class Lists<T> extends Generator<List<T>> {
    final Generator<T> valueType;
    final T[] buffer;

    @SuppressWarnings("unchecked")
    public Lists(String name, Generator<T> valueType, GeneratorConfig config) {
        super(ListType.getInstance(valueType.type, true), config, name, List.class);
        this.valueType = valueType;
        buffer = (T[]) new Object[(int) sizeDistribution.maxValue()];
    }

    @Override
    public void setSeed(long seed) {
        super.setSeed(seed);
        sizeDistribution.setSeed(seed);
        valueType.setSeed(seed * 31);
    }

    @Override
    public List<T> generate() {
        int size = (int) sizeDistribution.next();
        for (int i = 0; i < size; i++) buffer[i] = valueType.generate();
        return new ArrayList<>(Arrays.asList(Arrays.copyOf(buffer, size)));
    }

    @Override
    Object fromStoredValue(Object value) {
        if (value == null) return null;
        List<Object> list = new ArrayList<>();
        for (Object element : (List<?>) value) list.add(valueType.fromStoredValue(element));
        return list;
    }
}
