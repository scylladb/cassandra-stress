// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.Locale;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class FloatSerializer implements TypeSerializer<Float> {
    public static final FloatSerializer instance = new FloatSerializer();

    @Override
    public Float deserialize(ByteBuffer bytes) {
        if (bytes.remaining() == 0) return null;

        return ByteBufferUtil.toFloat(bytes);
    }

    @Override
    public ByteBuffer serialize(Float value) {
        return (value == null) ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() != 4 && bytes.remaining() != 0)
            throw new MarshalException(
                    String.format(Locale.ROOT, "Expected 4 or 0 byte value for a float (%d)", bytes.remaining()));
    }

    @Override
    public String toString(Float value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public Class<Float> getType() {
        return Float.class;
    }
}
