// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

import org.apache.cassandra.stress.util.ByteBufferUtil;

public class TimeSerializer implements TypeSerializer<Long>
{
    public static final TimeSerializer instance = new TimeSerializer();

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
        if (bytes.remaining() != 8)
            throw new MarshalException(String.format("Expected 8 byte long for time (%d)", bytes.remaining()));
    }

    public String toString(Long value)
    {
        if (value == null)
            return "null";

        int nano = (int)(value % 1000);
        value -= nano;
        value /= 1000;
        int micro = (int)(value % 1000);
        value -= micro;
        value /= 1000;
        int milli = (int)(value % 1000);
        value -= milli;
        value /= 1000;
        int seconds = (int)(value % 60);
        value -= seconds;
        value /= 60;
        int minutes = (int)(value % 60);
        value -= minutes;
        value /= 60;
        int hours = (int)(value % 24);
        value -= hours;
        value /= 24;
        assert(value == 0);

        StringBuilder sb = new StringBuilder();
        leftPadZeros(hours, 2, sb);
        sb.append(":");
        leftPadZeros(minutes, 2, sb);
        sb.append(":");
        leftPadZeros(seconds, 2, sb);
        sb.append(".");
        leftPadZeros(milli, 3, sb);
        leftPadZeros(micro, 3, sb);
        leftPadZeros(nano, 3, sb);
        return sb.toString();
    }

    private void leftPadZeros(int value, int digits, StringBuilder sb)
    {
        for (int i = 1; i < digits; ++i)
        {
            if (value < Math.pow(10, i))
                sb.append("0");
        }
        sb.append(value);
    }

    public Class<Long> getType()
    {
        return Long.class;
    }
}
