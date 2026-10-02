// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

public final class LongType extends AbstractType<Long>
{
    public static final LongType instance = new LongType();

    private LongType()
    {
        super(false);
    }

    public TypeSerializer<Long> getSerializer()
    {
        return LongSerializer.instance;
    }

    protected int compareCustom(ByteBuffer o1, ByteBuffer o2)
    {
        return compareSignedFirstByte(o1, o2);
    }
}
