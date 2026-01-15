package com.scylladb.stress.generate.values;

import com.scylladb.serializers.TypeSerializer;
import com.scylladb.utils.FastByteOperations;

import java.nio.ByteBuffer;
import java.util.Comparator;

public class Type<T> implements Comparator<ByteBuffer> {
    private final TypeSerializer<T> serializer;
    private final CustomComparer customComparer;
    private final ComparisonType comparisonType;

    public Type(TypeSerializer<T> serializer, ComparisonType comparisonType, CustomComparer customComparer) {
        this.serializer = serializer;
        this.customComparer = customComparer;
        this.comparisonType = comparisonType;

        if (comparisonType == ComparisonType.CUSTOM && customComparer == null) {
            throw new IllegalArgumentException("Custom comparer is required for CUSTOM comparison type");
        }
    }

    public ByteBuffer decompose(T value) {
        return serializer.serialize(value);
    }

    public T compose(ByteBuffer bytes) {
        return serializer.deserialize(bytes);
    }

    public TypeSerializer<T> getSerializer() {
        return serializer;
    }

    @Override
    public int compare(ByteBuffer o1, ByteBuffer o2) {
        return switch (comparisonType) {
            case NOT_COMPARABLE -> throw new UnsupportedOperationException();
            case BYTE_ORDER -> FastByteOperations.compareUnsigned(o1, o2);
            case CUSTOM -> {
                if (customComparer == null) {
                    throw new IllegalStateException("Custom comparer is required for CUSTOM comparison type");
                }
                yield customComparer.customCompare(o1, o2);
            }
        };
    }
}
