// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.apache.cassandra.stress.util.ByteBufferUtil;

public class SimpleDateSerializer implements TypeSerializer<Integer>
{
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    public static final SimpleDateSerializer instance = new SimpleDateSerializer();

    public Integer deserialize(ByteBuffer bytes)
    {
        return bytes.remaining() == 0 ? null : ByteBufferUtil.toInt(bytes);
    }

    public ByteBuffer serialize(Integer value)
    {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
        if (bytes.remaining() != 4)
            throw new MarshalException(String.format("Expected 4 byte long for date (%d)", bytes.remaining()));
    }

    public String toString(Integer value)
    {
        if (value == null)
            return "";

        return formatter.format(LocalDate.ofEpochDay((long) value - Integer.MIN_VALUE));
    }

    public Class<Integer> getType()
    {
        return Integer.class;
    }
}
