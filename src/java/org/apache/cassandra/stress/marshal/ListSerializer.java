// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.*;

public class ListSerializer<T> extends CollectionSerializer<List<T>>
{
    private static final Map<TypeSerializer<?>, ListSerializer> instances = new HashMap<TypeSerializer<?>, ListSerializer>();

    public final TypeSerializer<T> elements;

    public static synchronized <T> ListSerializer<T> getInstance(TypeSerializer<T> elements)
    {
        ListSerializer<T> t = instances.get(elements);
        if (t == null)
        {
            t = new ListSerializer<T>(elements);
            instances.put(elements, t);
        }
        return t;
    }

    private ListSerializer(TypeSerializer<T> elements)
    {
        this.elements = elements;
    }

    public List<ByteBuffer> serializeValues(List<T> values)
    {
        List<ByteBuffer> buffers = new ArrayList<>(values.size());
        for (T value : values)
            buffers.add(elements.serialize(value));
        return buffers;
    }

    public int getElementCount(List<T> value)
    {
        return value.size();
    }

    public void validateValues(ByteBuffer bytes)
    {
        try
        {
            ByteBuffer input = bytes.duplicate();
            int n = readCollectionSize(input);
            for (int i = 0; i < n; i++)
                elements.validate(readValue(input));

            if (input.hasRemaining())
                throw new MarshalException("Unexpected extraneous bytes after list value");
        }
        catch (BufferUnderflowException e)
        {
            throw new MarshalException("Not enough bytes to read a list");
        }
    }

    public List<T> deserializeValues(ByteBuffer bytes)
    {
        try
        {
            ByteBuffer input = bytes.duplicate();
            int n = readCollectionSize(input);

            if (n < 0)
                throw new MarshalException("The data cannot be deserialized as a list");

            List<T> l = new ArrayList<T>(Math.min(n, 256));
            for (int i = 0; i < n; i++)
            {
                ByteBuffer databb = readValue(input);
                if (databb != null)
                {
                    elements.validate(databb);
                    l.add(elements.deserialize(databb));
                }
                else
                {
                    l.add(null);
                }
            }

            if (input.hasRemaining())
                throw new MarshalException("Unexpected extraneous bytes after list value");

            return l;
        }
        catch (BufferUnderflowException e)
        {
            throw new MarshalException("Not enough bytes to read a list");
        }
    }

    public ByteBuffer getElement(ByteBuffer serializedList, int index)
    {
        try
        {
            ByteBuffer input = serializedList.duplicate();
            int n = readCollectionSize(input);
            if (n <= index)
                return null;

            for (int i = 0; i < index; i++)
            {
                int length = input.getInt();
                input.position(input.position() + length);
            }
            return readValue(input);
        }
        catch (BufferUnderflowException e)
        {
            throw new MarshalException("Not enough bytes to read a list");
        }
    }

    public String toString(List<T> value)
    {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        sb.append('[');
        for (T element : value)
        {
            if (isFirst)
                isFirst = false;
            else
                sb.append(", ");
            sb.append(elements.toString(element));
        }
        sb.append(']');
        return sb.toString();
    }

    public Class<List<T>> getType()
    {
        return (Class) List.class;
    }
}
