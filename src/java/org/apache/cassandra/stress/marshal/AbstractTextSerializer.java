// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public abstract class AbstractTextSerializer implements TypeSerializer<String> {
    private final Charset charset;

    protected AbstractTextSerializer(Charset charset) {
        this.charset = charset;
    }

    @Override
    public String deserialize(ByteBuffer bytes) {
        try {
            return ByteBufferUtil.string(bytes, charset);
        } catch (CharacterCodingException e) {
            throw new MarshalException("Invalid " + charset + " bytes " + ByteBufferUtil.bytesToHex(bytes), e);
        }
    }

    @Override
    public ByteBuffer serialize(String value) {
        return ByteBufferUtil.bytes(value, charset);
    }

    @Override
    public String toString(String value) {
        return value;
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public String toCQLLiteral(ByteBuffer buffer) {
        return buffer == null ? "null" : '\'' + deserialize(buffer).replace("'", "''") + '\'';
    }
}
