// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

import org.apache.cassandra.stress.util.ByteBufferUtil;

public final class ShortType extends AbstractType<Short>
{
    public static final ShortType instance = new ShortType();

    private ShortType()
    {
        super(false);
    }

    public TypeSerializer<Short> getSerializer()
    {
        return ShortSerializer.instance;
    }

    protected int compareCustom(ByteBuffer o1, ByteBuffer o2)
    {
        int diff = o1.get(o1.position()) - o2.get(o2.position());
        if (diff != 0)
            return diff;

        return ByteBufferUtil.compareUnsigned(o1, o2);
    }
}
