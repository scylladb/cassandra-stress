// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import org.apache.cassandra.stress.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class LongSerializer implements TypeSerializer<Long>
{
    public static final LongSerializer instance = new LongSerializer();

    public Long deserialize(ByteBuffer bytes)
    {
        return bytes.remaining() == 0 ? null : ByteBufferUtil.toLong(bytes);
    }

    public ByteBuffer serialize(Long value)
    {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
        if (bytes.remaining() != 8 && bytes.remaining() != 0)
            throw new MarshalException(String.format("Expected 8 or 0 byte long (%d)", bytes.remaining()));
    }

    public String toString(Long value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    public Class<Long> getType()
    {
        return Long.class;
    }
}
