// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v3.codecs;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.TypeCodec;
import java.nio.ByteBuffer;

public final class EpochDayCodec extends TypeCodec<Integer> {
    public EpochDayCodec() {
        super(DataType.date(), Integer.class);
    }

    @Override
    public ByteBuffer serialize(Integer value, ProtocolVersion protocolVersion) {
        return value == null ? null : TypeCodec.date().serialize(LocalDate.fromDaysSinceEpoch(value), protocolVersion);
    }

    @Override
    public Integer deserialize(ByteBuffer bytes, ProtocolVersion protocolVersion) {
        LocalDate date = TypeCodec.date().deserialize(bytes, protocolVersion);
        return date == null ? null : date.getDaysSinceEpoch();
    }

    @Override
    public String format(Integer value) {
        return TypeCodec.date().format(value == null ? null : LocalDate.fromDaysSinceEpoch(value));
    }

    @Override
    public Integer parse(String value) {
        LocalDate date = TypeCodec.date().parse(value);
        return date == null ? null : date.getDaysSinceEpoch();
    }
}
