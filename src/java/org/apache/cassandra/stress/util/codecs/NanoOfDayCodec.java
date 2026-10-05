// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util.codecs;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import java.nio.ByteBuffer;
import java.time.LocalTime;

public final class NanoOfDayCodec implements TypeCodec<Long> {
    @Override
    public GenericType<Long> getJavaType() {
        return GenericType.LONG;
    }

    @Override
    public DataType getCqlType() {
        return DataTypes.TIME;
    }

    @Override
    public boolean accepts(Object value) {
        return value instanceof Long;
    }

    @Override
    public boolean accepts(Class<?> javaClass) {
        return javaClass == Long.class;
    }

    @Override
    public ByteBuffer encode(Long value, ProtocolVersion protocolVersion) {
        return value == null ? null : TypeCodecs.TIME.encode(LocalTime.ofNanoOfDay(value), protocolVersion);
    }

    @Override
    public Long decode(ByteBuffer bytes, ProtocolVersion protocolVersion) {
        LocalTime time = TypeCodecs.TIME.decode(bytes, protocolVersion);
        return time == null ? null : time.toNanoOfDay();
    }

    @Override
    public String format(Long value) {
        return TypeCodecs.TIME.format(value == null ? null : LocalTime.ofNanoOfDay(value));
    }

    @Override
    public Long parse(String value) {
        LocalTime time = TypeCodecs.TIME.parse(value);
        return time == null ? null : time.toNanoOfDay();
    }
}
