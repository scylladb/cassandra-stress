// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import org.apache.cassandra.stress.util.ByteBufferUtil;

public class SimpleDateSerializer implements TypeSerializer<Integer>
{
    private static final DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd").withZone(DateTimeZone.UTC);
    private static final long minSupportedDateMillis = TimeUnit.DAYS.toMillis(Integer.MIN_VALUE);
    private static final long maxSupportedDateMillis = TimeUnit.DAYS.toMillis(Integer.MAX_VALUE);
    private static final long maxSupportedDays = (long)Math.pow(2,32) - 1;
    private static final long byteOrderShift = (long)Math.pow(2,31) * 2;

    private static final Pattern rawPattern = Pattern.compile("^-?\\d+$");
    public static final SimpleDateSerializer instance = new SimpleDateSerializer();

    public Integer deserialize(ByteBuffer bytes)
    {
        return bytes.remaining() == 0 ? null : ByteBufferUtil.toInt(bytes);
    }

    public ByteBuffer serialize(Integer value)
    {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value);
    }

    public static int dateStringToDays(String source) throws MarshalException
    {
        if (rawPattern.matcher(source).matches())
        {
            try
            {
                long result = Long.parseLong(source);

                if (result < 0 || result > maxSupportedDays)
                    throw new NumberFormatException("Input out of bounds: " + source);

                if (result >= Integer.MAX_VALUE)
                    result -= byteOrderShift;

                return (int) result;
            }
            catch (NumberFormatException e)
            {
                throw new MarshalException(String.format("Unable to make unsigned int (for date) from: '%s'", source), e);
            }
        }

        try
        {
            DateTime parsed = formatter.parseDateTime(source);
            long millis = parsed.getMillis();
            if (millis < minSupportedDateMillis)
                throw new MarshalException(String.format("Input date %s is less than min supported date %s", source, new LocalDate(minSupportedDateMillis).toString()));
            if (millis > maxSupportedDateMillis)
                throw new MarshalException(String.format("Input date %s is greater than max supported date %s", source, new LocalDate(maxSupportedDateMillis).toString()));

            return timeInMillisToDay(millis);
        }
        catch (IllegalArgumentException e1)
        {
            throw new MarshalException(String.format("Unable to coerce '%s' to a formatted date (long)", source), e1);
        }
    }

    public static int timeInMillisToDay(long millis)
    {
        Integer result = (int) TimeUnit.MILLISECONDS.toDays(millis);
        result -= Integer.MIN_VALUE;
        return result;
    }

    public static long dayToTimeInMillis(int days)
    {
        return TimeUnit.DAYS.toMillis(days - Integer.MIN_VALUE);
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

        return formatter.print(new LocalDate(dayToTimeInMillis(value), DateTimeZone.UTC));
    }

    public Class<Integer> getType()
    {
        return Integer.class;
    }
}
