// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SetType<T> extends AbstractType<Set<T>> {
    private static final Map<AbstractType<?>, SetType<?>> instances = new ConcurrentHashMap<>();

    public final AbstractType<T> elements;
    private final SetSerializer<T> serializer;
    public final boolean isMultiCell;

    @SuppressWarnings("unchecked")
    public static <T> SetType<T> getInstance(AbstractType<T> elements, boolean isMultiCell) {
        return (SetType<T>) instances.computeIfAbsent(elements, e -> new SetType<>(elements, isMultiCell));
    }

    private SetType(AbstractType<T> elements, boolean isMultiCell) {
        super(false);
        this.elements = elements;
        this.serializer = SetSerializer.getInstance(elements.getSerializer(), elements);
        this.isMultiCell = isMultiCell;
    }

    @Override
    public SetSerializer<T> getSerializer() {
        return serializer;
    }

    @Override
    protected int compareCustom(ByteBuffer left, ByteBuffer right) {
        return compareListOrSet(elements, left, right);
    }
}
