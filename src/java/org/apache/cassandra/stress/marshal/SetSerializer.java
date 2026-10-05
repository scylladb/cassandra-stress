// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SetSerializer<T> extends CollectionSerializer<Set<T>> {
    private static final Map<TypeSerializer<?>, SetSerializer<?>> instances = new ConcurrentHashMap<>();

    public final TypeSerializer<T> elements;
    private final Comparator<ByteBuffer> comparator;

    @SuppressWarnings("unchecked")
    public static <T> SetSerializer<T> getInstance(
            TypeSerializer<T> elements, Comparator<ByteBuffer> elementComparator) {
        return (SetSerializer<T>)
                instances.computeIfAbsent(elements, e -> new SetSerializer<>(elements, elementComparator));
    }

    private SetSerializer(TypeSerializer<T> elements, Comparator<ByteBuffer> comparator) {
        this.elements = elements;
        this.comparator = comparator;
    }

    @Override
    public List<ByteBuffer> serializeValues(Set<T> values) {
        List<ByteBuffer> buffers = new ArrayList<>(values.size());
        for (T value : values) buffers.add(elements.serialize(value));
        Collections.sort(buffers, comparator);
        return buffers;
    }

    @Override
    public int getElementCount(Set<T> value) {
        return value.size();
    }

    @Override
    public void validateValues(ByteBuffer bytes) {
        try {
            if (bytes.remaining() == 0) {
                return;
            }
            ByteBuffer input = bytes.duplicate();
            int n = readCollectionSize(input);
            for (int i = 0; i < n; i++) elements.validate(readValue(input));
            if (input.hasRemaining()) throw new MarshalException("Unexpected extraneous bytes after set value");
        } catch (BufferUnderflowException e) {
            throw new MarshalException("Not enough bytes to read a set", e);
        }
    }

    @Override
    public Set<T> deserializeValues(ByteBuffer bytes) {
        try {
            ByteBuffer input = bytes.duplicate();
            int n = readCollectionSize(input);

            if (n < 0) throw new MarshalException("The data cannot be deserialized as a set");

            Set<T> l = new LinkedHashSet<T>(Math.min(n, 256));

            for (int i = 0; i < n; i++) {
                ByteBuffer databb = readValue(input);
                elements.validate(databb);
                l.add(elements.deserialize(databb));
            }
            if (input.hasRemaining()) throw new MarshalException("Unexpected extraneous bytes after set value");
            return l;
        } catch (BufferUnderflowException e) {
            throw new MarshalException("Not enough bytes to read a set", e);
        }
    }

    @Override
    public String toString(Set<T> value) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean isFirst = true;
        for (T element : value) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(", ");
            }
            sb.append(elements.toString(element));
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public Class<Set<T>> getType() {
        return (Class) Set.class;
    }
}
