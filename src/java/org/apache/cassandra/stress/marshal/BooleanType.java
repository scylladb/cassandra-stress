// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

public final class BooleanType extends AbstractType<Boolean>
{
    public static final BooleanType instance = new BooleanType();

    private BooleanType()
    {
        super(false);
    }

    public TypeSerializer<Boolean> getSerializer()
    {
        return BooleanSerializer.instance;
    }

    protected int compareCustom(ByteBuffer o1, ByteBuffer o2)
    {
        if (!o1.hasRemaining() || !o2.hasRemaining())
            return o1.hasRemaining() ? 1 : o2.hasRemaining() ? -1 : 0;

        byte b1 = o1.get(o1.position());
        byte b2 = o2.get(o2.position());
        if (b1 == 0)
            return b2 == 0 ? 0 : -1;
        return b2 == 0 ? 1 : 0;
    }
}
