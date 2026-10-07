// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class TimestampSerializer implements TypeSerializer<Date> {
    private static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mmXX";

    private static final ThreadLocal<SimpleDateFormat> FORMATTER =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(DEFAULT_FORMAT, Locale.ROOT));

    private static final String UTC_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";
    private static final ThreadLocal<SimpleDateFormat> FORMATTER_UTC = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat(UTC_FORMAT, Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    });

    public static final TimestampSerializer instance = new TimestampSerializer();

    @Override
    public Date deserialize(ByteBuffer bytes) {
        return bytes.remaining() == 0 ? null : new Date(ByteBufferUtil.toLong(bytes));
    }

    @Override
    public ByteBuffer serialize(Date value) {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBufferUtil.bytes(value.getTime());
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() != 8 && bytes.remaining() != 0)
            throw new MarshalException(
                    String.format(Locale.ROOT, "Expected 8 or 0 byte long for date (%d)", bytes.remaining()));
    }

    @Override
    public String toString(Date value) {
        return value == null ? "" : FORMATTER.get().format(value);
    }

    public String toStringUTC(Date value) {
        return value == null ? "" : FORMATTER_UTC.get().format(value);
    }

    @Override
    public Class<Date> getType() {
        return Date.class;
    }

    @Override
    public String toCQLLiteral(ByteBuffer buffer) {
        return buffer == null || !buffer.hasRemaining()
                ? "null"
                : FORMATTER_UTC.get().format(deserialize(buffer));
    }
}
