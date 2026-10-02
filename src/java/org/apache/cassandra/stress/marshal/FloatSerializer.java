// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import org.apache.cassandra.stress.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class FloatSerializer implements TypeSerializer<Float>
{
    public static final FloatSerializer instance = new FloatSerializer();

    public Float deserialize(ByteBuffer bytes)
    {
        if (bytes.remaining() == 0)
            return null;

        return ByteBufferUtil.toFloat(bytes);
    }

    public ByteBuffer serialize(Float value)
    {
        return (value == null) ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
        if (bytes.remaining() != 4 && bytes.remaining() != 0)
            throw new MarshalException(String.format("Expected 4 or 0 byte value for a float (%d)", bytes.remaining()));
    }

    public String toString(Float value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    public Class<Float> getType()
    {
        return Float.class;
    }
}
