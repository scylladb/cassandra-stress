// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.Locale;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class BooleanSerializer implements TypeSerializer<Boolean> {
    private static final ByteBuffer TRUE = ByteBuffer.wrap(new byte[] {1});
    private static final ByteBuffer FALSE = ByteBuffer.wrap(new byte[] {0});

    public static final BooleanSerializer instance = new BooleanSerializer();

    @Override
    public Boolean deserialize(ByteBuffer bytes) {
        if (bytes == null || bytes.remaining() == 0) return null;

        byte value = bytes.get(bytes.position());
        return value != 0;
    }

    @Override
    public ByteBuffer serialize(Boolean value) {
        return (value == null) ? ByteBufferUtil.EMPTY_BYTE_BUFFER : value ? TRUE : FALSE;
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() != 1 && bytes.remaining() != 0)
            throw new MarshalException(
                    String.format(Locale.ROOT, "Expected 1 or 0 byte value (%d)", bytes.remaining()));
    }

    @Override
    public String toString(Boolean value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public Class<Boolean> getType() {
        return Boolean.class;
    }
}
