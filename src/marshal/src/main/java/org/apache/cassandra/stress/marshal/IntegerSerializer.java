// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class IntegerSerializer implements TypeSerializer<BigInteger> {
    public static final IntegerSerializer instance = new IntegerSerializer();

    @Override
    public BigInteger deserialize(ByteBuffer bytes) {
        return bytes.hasRemaining() ? new BigInteger(ByteBufferUtil.getArray(bytes)) : null;
    }

    @Override
    public ByteBuffer serialize(BigInteger value) {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBuffer.wrap(value.toByteArray());
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {}

    @Override
    public String toString(BigInteger value) {
        return value == null ? "" : value.toString(10);
    }

    @Override
    public Class<BigInteger> getType() {
        return BigInteger.class;
    }
}
