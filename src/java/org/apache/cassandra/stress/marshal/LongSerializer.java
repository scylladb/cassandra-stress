// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class LongSerializer implements TypeSerializer<Long> {
    public static final LongSerializer instance = new LongSerializer();

    @Override
    public Long deserialize(ByteBuffer bytes) {
        return bytes.remaining() == 0 ? null : ByteBufferUtil.toLong(bytes);
    }

    @Override
    public ByteBuffer serialize(Long value) {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() != 8 && bytes.remaining() != 0)
            throw new MarshalException(String.format("Expected 8 or 0 byte long (%d)", bytes.remaining()));
    }

    @Override
    public String toString(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public Class<Long> getType() {
        return Long.class;
    }
}
