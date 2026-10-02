// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import org.apache.cassandra.stress.util.ByteBufferUtil;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public class IntegerSerializer implements TypeSerializer<BigInteger>
{
    public static final IntegerSerializer instance = new IntegerSerializer();

    public BigInteger deserialize(ByteBuffer bytes)
    {
        return bytes.hasRemaining() ? new BigInteger(ByteBufferUtil.getArray(bytes)) : null;
    }

    public ByteBuffer serialize(BigInteger value)
    {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBuffer.wrap(value.toByteArray());
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
    }

    public String toString(BigInteger value)
    {
        return value == null ? "" : value.toString(10);
    }

    public Class<BigInteger> getType()
    {
        return BigInteger.class;
    }
}
