// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class SimpleDateSerializer implements TypeSerializer<Integer> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    public static final SimpleDateSerializer instance = new SimpleDateSerializer();

    @Override
    public Integer deserialize(ByteBuffer bytes) {
        return bytes.remaining() == 0 ? null : ByteBufferUtil.toInt(bytes);
    }

    @Override
    public ByteBuffer serialize(Integer value) {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() != 4)
            throw new MarshalException(
                    String.format(Locale.ROOT, "Expected 4 byte long for date (%d)", bytes.remaining()));
    }

    @Override
    public String toString(Integer value) {
        if (value == null) return "";

        return FORMATTER.format(LocalDate.ofEpochDay(value));
    }

    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }
}
