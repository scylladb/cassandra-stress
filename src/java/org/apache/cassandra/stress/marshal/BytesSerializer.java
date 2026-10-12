// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class BytesSerializer implements TypeSerializer<ByteBuffer> {
    public static final BytesSerializer instance = new BytesSerializer();

    @Override
    public ByteBuffer serialize(ByteBuffer bytes) {
        return bytes.duplicate();
    }

    @Override
    public ByteBuffer deserialize(ByteBuffer value) {
        return value;
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {}

    @Override
    public String toString(ByteBuffer value) {
        return ByteBufferUtil.bytesToHex(value);
    }

    @Override
    public Class<ByteBuffer> getType() {
        return ByteBuffer.class;
    }

    @Override
    public String toCQLLiteral(ByteBuffer buffer) {
        return buffer == null ? "null" : "0x" + toString(deserialize(buffer));
    }
}
