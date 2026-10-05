// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public abstract class CollectionSerializer<T> implements TypeSerializer<T> {
    protected abstract List<ByteBuffer> serializeValues(T value);

    protected abstract int getElementCount(T value);

    public abstract T deserializeValues(ByteBuffer buffer);

    public abstract void validateValues(ByteBuffer buffer);

    @Override
    public ByteBuffer serialize(T value) {
        List<ByteBuffer> values = serializeValues(value);

        return pack(values, getElementCount(value));
    }

    @Override
    public T deserialize(ByteBuffer bytes) {
        return deserializeValues(bytes);
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        validateValues(bytes);
    }

    public static ByteBuffer pack(Collection<ByteBuffer> buffers, int elements) {
        int size = 0;
        for (ByteBuffer bb : buffers) size += sizeOfValue(bb);

        ByteBuffer result = ByteBuffer.allocate(sizeOfCollectionSize(elements) + size);
        writeCollectionSize(result, elements);
        for (ByteBuffer bb : buffers) writeValue(result, bb);
        return (ByteBuffer) result.flip();
    }

    protected static void writeCollectionSize(ByteBuffer output, int elements) {
        output.putInt(elements);
    }

    public static int readCollectionSize(ByteBuffer input) {
        return input.getInt();
    }

    protected static int sizeOfCollectionSize(int elements) {
        return 4;
    }

    public static void writeValue(ByteBuffer output, ByteBuffer value) {
        if (value == null) {
            output.putInt(-1);
            return;
        }

        output.putInt(value.remaining());
        output.put(value.duplicate());
    }

    public static ByteBuffer readValue(ByteBuffer input) {
        int size = input.getInt();
        if (size < 0) return null;

        return ByteBufferUtil.readBytes(input, size);
    }

    public static int sizeOfValue(ByteBuffer value) {
        return value == null ? 4 : 4 + value.remaining();
    }
}
