// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.util.HashMap;
import java.util.Set;
import java.util.Map;

public final class SetType<T> extends AbstractType<Set<T>>
{
    private static final Map<AbstractType<?>, SetType<?>> instances = new HashMap<>();

    public final AbstractType<T> elements;
    private final SetSerializer<T> serializer;
    public final boolean isMultiCell;

    @SuppressWarnings("unchecked")
    public static synchronized <T> SetType<T> getInstance(AbstractType<T> elements, boolean isMultiCell)
    {
        SetType<T> type = (SetType<T>) instances.get(elements);
        if (type == null)
        {
            type = new SetType<>(elements, isMultiCell);
            instances.put(elements, type);
        }
        return type;
    }

    private SetType(AbstractType<T> elements, boolean isMultiCell)
    {
        super(false);
        this.elements = elements;
        this.serializer = SetSerializer.getInstance(elements.getSerializer(), elements);
        this.isMultiCell = isMultiCell;
    }

    public SetSerializer<T> getSerializer()
    {
        return serializer;
    }
}
