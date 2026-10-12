// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ListSerializer<T> extends CollectionSerializer<List<T>> {
    private static final Map<TypeSerializer<?>, ListSerializer<?>> instances = new ConcurrentHashMap<>();

    public final TypeSerializer<T> elements;

    @SuppressWarnings("unchecked")
    public static <T> ListSerializer<T> getInstance(TypeSerializer<T> elements) {
        return (ListSerializer<T>) instances.computeIfAbsent(elements, e -> new ListSerializer<>(elements));
    }

    private ListSerializer(TypeSerializer<T> elements) {
        this.elements = elements;
    }

    @Override
    public List<ByteBuffer> serializeValues(List<T> values) {
        List<ByteBuffer> buffers = new ArrayList<>(values.size());
        for (T value : values) {
            buffers.add(elements.serialize(value));
        }
        return buffers;
    }

    @Override
    public int getElementCount(List<T> value) {
        return value.size();
    }

    @Override
    public void validateValues(ByteBuffer bytes) {
        try {
            ByteBuffer input = bytes.duplicate();
            int n = readCollectionSize(input);
            for (int i = 0; i < n; i++) {
                elements.validate(readValue(input));
            }

            if (input.hasRemaining()) {
                throw new MarshalException("Unexpected extraneous bytes after list value");
            }
        } catch (BufferUnderflowException e) {
            throw new MarshalException("Not enough bytes to read a list", e);
        }
    }

    @Override
    public List<T> deserializeValues(ByteBuffer bytes) {
        try {
            ByteBuffer input = bytes.duplicate();
            int n = readCollectionSize(input);

            if (n < 0) {
                throw new MarshalException("The data cannot be deserialized as a list");
            }

            List<T> l = new ArrayList<T>(Math.min(n, 256));
            for (int i = 0; i < n; i++) {
                ByteBuffer databb = readValue(input);
                if (databb != null) {
                    elements.validate(databb);
                    l.add(elements.deserialize(databb));
                } else {
                    l.add(null);
                }
            }

            if (input.hasRemaining()) {
                throw new MarshalException("Unexpected extraneous bytes after list value");
            }

            return l;
        } catch (BufferUnderflowException e) {
            throw new MarshalException("Not enough bytes to read a list", e);
        }
    }

    public ByteBuffer getElement(ByteBuffer serializedList, int index) {
        try {
            ByteBuffer input = serializedList.duplicate();
            int n = readCollectionSize(input);
            if (n <= index) {
                return null;
            }

            for (int i = 0; i < index; i++) {
                int length = input.getInt();
                input.position(input.position() + length);
            }
            return readValue(input);
        } catch (BufferUnderflowException e) {
            throw new MarshalException("Not enough bytes to read a list", e);
        }
    }

    @Override
    public String toString(List<T> value) {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        sb.append('[');
        for (T element : value) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(", ");
            }
            sb.append(elements.toString(element));
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public Class<List<T>> getType() {
        return (Class) List.class;
    }
}
