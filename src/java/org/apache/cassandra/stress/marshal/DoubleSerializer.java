// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class DoubleSerializer implements TypeSerializer<Double> {
    public static final DoubleSerializer instance = new DoubleSerializer();

    @Override
    public Double deserialize(ByteBuffer bytes) {
        if (bytes.remaining() == 0) return null;
        return ByteBufferUtil.toDouble(bytes);
    }

    @Override
    public ByteBuffer serialize(Double value) {
        return (value == null) ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() != 8 && bytes.remaining() != 0)
            throw new MarshalException(
                    String.format("Expected 8 or 0 byte value for a double (%d)", bytes.remaining()));
    }

    @Override
    public String toString(Double value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public Class<Double> getType() {
        return Double.class;
    }
}
