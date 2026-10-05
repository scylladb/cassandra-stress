// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class ByteSerializer implements TypeSerializer<Byte> {
    public static final ByteSerializer instance = new ByteSerializer();

    @Override
    public Byte deserialize(ByteBuffer bytes) {
        return bytes == null || bytes.remaining() == 0 ? null : bytes.get(bytes.position());
    }

    @Override
    public ByteBuffer serialize(Byte value) {
        return value == null
                ? ByteBufferUtil.EMPTY_BYTE_BUFFER
                : ByteBuffer.allocate(1).put(0, value);
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() != 1)
            throw new MarshalException(String.format("Expected 1 byte for a tinyint (%d)", bytes.remaining()));
    }

    @Override
    public String toString(Byte value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public Class<Byte> getType() {
        return Byte.class;
    }
}
