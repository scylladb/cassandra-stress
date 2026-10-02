// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ListType<T> extends AbstractType<List<T>>
{
    private static final Map<AbstractType<?>, ListType<?>> instances = new HashMap<>();

    public final AbstractType<T> elements;
    private final ListSerializer<T> serializer;
    public final boolean isMultiCell;

    @SuppressWarnings("unchecked")
    public static synchronized <T> ListType<T> getInstance(AbstractType<T> elements, boolean isMultiCell)
    {
        ListType<T> type = (ListType<T>) instances.get(elements);
        if (type == null)
        {
            type = new ListType<>(elements, isMultiCell);
            instances.put(elements, type);
        }
        return type;
    }

    private ListType(AbstractType<T> elements, boolean isMultiCell)
    {
        super(false);
        this.elements = elements;
        this.serializer = ListSerializer.getInstance(elements.getSerializer());
        this.isMultiCell = isMultiCell;
    }

    public ListSerializer<T> getSerializer()
    {
        return serializer;
    }
}
