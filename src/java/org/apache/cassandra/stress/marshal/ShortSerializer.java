// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class ShortSerializer implements TypeSerializer<Short> {
    public static final ShortSerializer instance = new ShortSerializer();

    @Override
    public Short deserialize(ByteBuffer bytes) {
        return bytes.remaining() == 0 ? null : ByteBufferUtil.toShort(bytes);
    }

    @Override
    public ByteBuffer serialize(Short value) {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value.shortValue());
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() != 2)
            throw new MarshalException(String.format("Expected 2 bytes for a smallint (%d)", bytes.remaining()));
    }

    @Override
    public String toString(Short value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public Class<Short> getType() {
        return Short.class;
    }
}
