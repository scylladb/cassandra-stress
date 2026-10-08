// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Locale;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class DecimalSerializer implements TypeSerializer<BigDecimal> {
    public static final DecimalSerializer instance = new DecimalSerializer();

    @Override
    public BigDecimal deserialize(ByteBuffer bytes) {
        if (bytes == null || bytes.remaining() == 0) {
            return null;
        }

        bytes = bytes.duplicate();
        int scale = bytes.getInt();
        byte[] bibytes = new byte[bytes.remaining()];
        bytes.get(bibytes);

        BigInteger bi = new BigInteger(bibytes);
        return new BigDecimal(bi, scale);
    }

    @Override
    public ByteBuffer serialize(BigDecimal value) {
        if (value == null) {
            return ByteBufferUtil.EMPTY_BYTE_BUFFER;
        }

        BigInteger bi = value.unscaledValue();
        int scale = value.scale();
        byte[] bibytes = bi.toByteArray();

        ByteBuffer bytes = ByteBuffer.allocate(4 + bibytes.length);
        bytes.putInt(scale);
        bytes.put(bibytes);
        bytes.rewind();
        return bytes;
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() != 0 && bytes.remaining() < 4) {
            throw new MarshalException(
                    String.format(Locale.ROOT, "Expected 0 or at least 4 bytes (%d)", bytes.remaining()));
        }
    }

    @Override
    public String toString(BigDecimal value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public Class<BigDecimal> getType() {
        return BigDecimal.class;
    }
}
