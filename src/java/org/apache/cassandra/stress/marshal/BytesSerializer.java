// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import org.apache.cassandra.stress.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class BytesSerializer implements TypeSerializer<ByteBuffer>
{
    public static final BytesSerializer instance = new BytesSerializer();

    public ByteBuffer serialize(ByteBuffer bytes)
    {
        return bytes.duplicate();
    }

    public ByteBuffer deserialize(ByteBuffer value)
    {
        return value;
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
    }

    public String toString(ByteBuffer value)
    {
        return ByteBufferUtil.bytesToHex(value);
    }

    public Class<ByteBuffer> getType()
    {
        return ByteBuffer.class;
    }

    @Override
    public String toCQLLiteral(ByteBuffer buffer)
    {
        return buffer == null
             ? "null"
             : "0x" + toString(deserialize(buffer));
    }
}
