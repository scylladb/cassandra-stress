/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.scylladb.serializers;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.scylladb.utils.ByteBufferUtil;

// For byte-order comparability, we shift by Integer.MIN_VALUE and treat the data as an unsigned integer ranging from
// min date to max date w/epoch sitting in the center @ 2^31
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
