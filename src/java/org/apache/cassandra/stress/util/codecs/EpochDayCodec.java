// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util.codecs;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import java.nio.ByteBuffer;
import java.time.LocalDate;

public final class EpochDayCodec implements TypeCodec<Integer> {
    @Override
    public GenericType<Integer> getJavaType() {
        return GenericType.INTEGER;
    }

    @Override
    public DataType getCqlType() {
        return DataTypes.DATE;
    }

    @Override
    public boolean accepts(Object value) {
        return value instanceof Integer;
    }

    @Override
    public boolean accepts(Class<?> javaClass) {
        return javaClass == Integer.class;
    }

    @Override
    public ByteBuffer encode(Integer value, ProtocolVersion protocolVersion) {
        return value == null ? null : TypeCodecs.DATE.encode(LocalDate.ofEpochDay(value), protocolVersion);
    }

    @Override
    public Integer decode(ByteBuffer bytes, ProtocolVersion protocolVersion) {
        LocalDate date = TypeCodecs.DATE.decode(bytes, protocolVersion);
        return date == null ? null : Math.toIntExact(date.toEpochDay());
    }

    @Override
    public String format(Integer value) {
        return TypeCodecs.DATE.format(value == null ? null : LocalDate.ofEpochDay(value));
    }

    @Override
    public Integer parse(String value) {
        LocalDate date = TypeCodecs.DATE.parse(value);
        return date == null ? null : Math.toIntExact(date.toEpochDay());
    }
}
