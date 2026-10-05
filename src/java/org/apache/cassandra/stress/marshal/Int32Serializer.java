// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class Int32Serializer implements TypeSerializer<Integer> {
    public static final Int32Serializer instance = new Int32Serializer();

    @Override
    public Integer deserialize(ByteBuffer bytes) {
        return bytes.remaining() == 0 ? null : ByteBufferUtil.toInt(bytes);
    }

    @Override
    public ByteBuffer serialize(Integer value) {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() != 4 && bytes.remaining() != 0)
            throw new MarshalException(String.format("Expected 4 or 0 byte int (%d)", bytes.remaining()));
    }

    @Override
    public String toString(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }
}
