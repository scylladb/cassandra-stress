// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import io.netty.util.concurrent.FastThreadLocal;
import org.apache.cassandra.stress.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.DateUtils;

public class TimestampSerializer implements TypeSerializer<Date>
{
    private static final String[] dateStringPatterns = new String[]
    {
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm z",
            "yyyy-MM-dd HH:mm zz",
            "yyyy-MM-dd HH:mm zzz",
            "yyyy-MM-dd HH:mmX",
            "yyyy-MM-dd HH:mmXX",
            "yyyy-MM-dd HH:mmXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss z",
            "yyyy-MM-dd HH:mm:ss zz",
            "yyyy-MM-dd HH:mm:ss zzz",
            "yyyy-MM-dd HH:mm:ssX",
            "yyyy-MM-dd HH:mm:ssXX",
            "yyyy-MM-dd HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss.SSS z",
            "yyyy-MM-dd HH:mm:ss.SSS zz",
            "yyyy-MM-dd HH:mm:ss.SSS zzz",
            "yyyy-MM-dd HH:mm:ss.SSSX",
            "yyyy-MM-dd HH:mm:ss.SSSXX",
            "yyyy-MM-dd HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd'T'HH:mm z",
            "yyyy-MM-dd'T'HH:mm zz",
            "yyyy-MM-dd'T'HH:mm zzz",
            "yyyy-MM-dd'T'HH:mmX",
            "yyyy-MM-dd'T'HH:mmXX",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss zz",
            "yyyy-MM-dd'T'HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ssXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS z",
            "yyyy-MM-dd'T'HH:mm:ss.SSS zz",
            "yyyy-MM-dd'T'HH:mm:ss.SSS zzz",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd",
            "yyyy-MM-dd z",
            "yyyy-MM-dd zz",
            "yyyy-MM-dd zzz",
            "yyyy-MM-ddX",
            "yyyy-MM-ddXX",
            "yyyy-MM-ddXXX"
    };

    private static final String DEFAULT_FORMAT = dateStringPatterns[6];
    private static final Pattern timestampPattern = Pattern.compile("^-?\\d+$");

    private static final FastThreadLocal<SimpleDateFormat> FORMATTER = new FastThreadLocal<SimpleDateFormat>()
    {
        protected SimpleDateFormat initialValue()
        {
            return new SimpleDateFormat(DEFAULT_FORMAT);
        }
    };

    private static final String UTC_FORMAT = dateStringPatterns[40];
    private static final FastThreadLocal<SimpleDateFormat> FORMATTER_UTC = new FastThreadLocal<SimpleDateFormat>()
    {
        protected SimpleDateFormat initialValue()
        {
            SimpleDateFormat sdf = new SimpleDateFormat(UTC_FORMAT);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
        }
    };

    private static final String TO_JSON_FORMAT = dateStringPatterns[19];
    private static final FastThreadLocal<SimpleDateFormat> FORMATTER_TO_JSON = new FastThreadLocal<SimpleDateFormat>()
    {
        protected SimpleDateFormat initialValue()
        {
            SimpleDateFormat sdf = new SimpleDateFormat(TO_JSON_FORMAT);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
        }
    };

    public static final TimestampSerializer instance = new TimestampSerializer();

    public Date deserialize(ByteBuffer bytes)
    {
        return bytes.remaining() == 0 ? null : new Date(ByteBufferUtil.toLong(bytes));
    }

    public ByteBuffer serialize(Date value)
    {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value.getTime());
    }

    public static long dateStringToTimestamp(String source) throws MarshalException
    {
        if (source.equalsIgnoreCase("now"))
            return System.currentTimeMillis();

        if (timestampPattern.matcher(source).matches())
        {
            try
            {
                return Long.parseLong(source);
            }
            catch (NumberFormatException e)
            {
                throw new MarshalException(String.format("Unable to make long (for date) from: '%s'", source), e);
            }
        }

        try
        {
            return DateUtils.parseDateStrictly(source, dateStringPatterns).getTime();
        }
        catch (ParseException e1)
        {
            throw new MarshalException(String.format("Unable to coerce '%s' to a formatted date (long)", source), e1);
        }
    }

    public static SimpleDateFormat getJsonDateFormatter()
    {
    	return FORMATTER_TO_JSON.get();
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
        if (bytes.remaining() != 8 && bytes.remaining() != 0)
            throw new MarshalException(String.format("Expected 8 or 0 byte long for date (%d)", bytes.remaining()));
    }

    public String toString(Date value)
    {
        return value == null ? "" : FORMATTER.get().format(value);
    }

    public String toStringUTC(Date value)
    {
        return value == null ? "" : FORMATTER_UTC.get().format(value);
    }

    public Class<Date> getType()
    {
        return Date.class;
    }

    @Override
    public String toCQLLiteral(ByteBuffer buffer)
    {
        return buffer == null || !buffer.hasRemaining()
             ? "null"
             : FORMATTER_UTC.get().format(deserialize(buffer));
    }
}
