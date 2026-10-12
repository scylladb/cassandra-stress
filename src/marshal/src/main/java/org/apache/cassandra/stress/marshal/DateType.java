// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.Date;

public final class DateType extends AbstractType<Date> {
    public static final DateType instance = new DateType();

    private DateType() {
        super(false);
    }

    @Override
    public TypeSerializer<Date> getSerializer() {
        return TimestampSerializer.instance;
    }

    @Override
    protected int compareCustom(ByteBuffer o1, ByteBuffer o2) {
        return compareSignedFirstByte(o1, o2);
    }
}
