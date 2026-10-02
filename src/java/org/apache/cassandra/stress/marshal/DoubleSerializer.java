// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import org.apache.cassandra.stress.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class DoubleSerializer implements TypeSerializer<Double>
{
    public static final DoubleSerializer instance = new DoubleSerializer();

    public Double deserialize(ByteBuffer bytes)
    {
        if (bytes.remaining() == 0)
            return null;
        return ByteBufferUtil.toDouble(bytes);
    }

    public ByteBuffer serialize(Double value)
    {
        return (value == null) ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
        if (bytes.remaining() != 8 && bytes.remaining() != 0)
            throw new MarshalException(String.format("Expected 8 or 0 byte value for a double (%d)", bytes.remaining()));
    }

    public String toString(Double value)
    {
        return value == null ? "" : value.toString();
    }

    public Class<Double> getType()
    {
        return Double.class;
    }
}
