// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ListType<T> extends AbstractType<List<T>> {
    private static final Map<AbstractType<?>, ListType<?>> instances = new ConcurrentHashMap<>();

    public final AbstractType<T> elements;
    private final ListSerializer<T> serializer;
    public final boolean isMultiCell;

    @SuppressWarnings("unchecked")
    public static <T> ListType<T> getInstance(AbstractType<T> elements, boolean isMultiCell) {
        return (ListType<T>) instances.computeIfAbsent(elements, e -> new ListType<>(elements, isMultiCell));
    }

    private ListType(AbstractType<T> elements, boolean isMultiCell) {
        super(false);
        this.elements = elements;
        this.serializer = ListSerializer.getInstance(elements.getSerializer());
        this.isMultiCell = isMultiCell;
    }

    @Override
    public ListSerializer<T> getSerializer() {
        return serializer;
    }
}
