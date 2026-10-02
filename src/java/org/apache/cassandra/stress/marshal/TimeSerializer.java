// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.cassandra.stress.util.ByteBufferUtil;

public class TimeSerializer implements TypeSerializer<Long>
{
    public static final Pattern timePattern = Pattern.compile("^-?\\d+$");
    public static final TimeSerializer instance = new TimeSerializer();

    public Long deserialize(ByteBuffer bytes)
    {
        return bytes.remaining() == 0 ? null : ByteBufferUtil.toLong(bytes);
    }

    public ByteBuffer serialize(Long value)
    {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    public static Long timeStringToLong(String source) throws MarshalException
    {
        if (timePattern.matcher(source).matches())
        {
            try
            {
                long result = Long.parseLong(source);
                if (result < 0 || result >= TimeUnit.DAYS.toNanos(1))
                    throw new NumberFormatException("Input long out of bounds: " + source);
                return result;
            }
            catch (NumberFormatException e)
            {
                throw new MarshalException(String.format("Unable to make long (for time) from: '%s'", source), e);
            }
        }

        try
        {
            return parseTimeStrictly(source);
        }
        catch (IllegalArgumentException e1)
        {
            throw new MarshalException(String.format("(TimeType) Unable to coerce '%s' to a formatted time (long)", source), e1);
        }
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

    private static Long parseTimeStrictly(String s) throws IllegalArgumentException
    {
        String nanos_s;

        long hour;
        long minute;
        long second;
        long a_nanos = 0;

        String formatError = "Timestamp format must be hh:mm:ss[.fffffffff]";
        String zeros = "000000000";

        if (s == null)
            throw new java.lang.IllegalArgumentException(formatError);
        s = s.trim();

        int firstColon = s.indexOf(':');
        int secondColon = s.indexOf(':', firstColon+1);

        if (firstColon > 0 && secondColon > 0 && secondColon < s.length() - 1)
        {
            int period = s.indexOf('.', secondColon+1);
            hour = Integer.parseInt(s.substring(0, firstColon));
            if (hour < 0 || hour >= 24)
                throw new IllegalArgumentException("Hour out of bounds.");

            minute = Integer.parseInt(s.substring(firstColon + 1, secondColon));
            if (minute < 0 || minute >= 60)
                throw new IllegalArgumentException("Minute out of bounds.");

            if (period > 0 && period < s.length() - 1)
            {
                second = Integer.parseInt(s.substring(secondColon + 1, period));
                if (second < 0 || second >= 60)
                    throw new IllegalArgumentException("Second out of bounds.");

                nanos_s = s.substring(period + 1);
                if (nanos_s.length() > 9)
                    throw new IllegalArgumentException(formatError);
                if (!Character.isDigit(nanos_s.charAt(0)))
                    throw new IllegalArgumentException(formatError);
                nanos_s = nanos_s + zeros.substring(0, 9 - nanos_s.length());
                a_nanos = Integer.parseInt(nanos_s);
            }
            else if (period > 0)
                throw new IllegalArgumentException(formatError);
            else
            {
                second = Integer.parseInt(s.substring(secondColon + 1));
                if (second < 0 || second >= 60)
                    throw new IllegalArgumentException("Second out of bounds.");
            }
        }
        else
            throw new IllegalArgumentException(formatError);

        long rawTime = 0;
        rawTime += TimeUnit.HOURS.toNanos(hour);
        rawTime += TimeUnit.MINUTES.toNanos(minute);
        rawTime += TimeUnit.SECONDS.toNanos(second);
        rawTime += a_nanos;
        return rawTime;
    }
}
