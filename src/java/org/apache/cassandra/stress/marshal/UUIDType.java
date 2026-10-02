// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.UUID;

import com.google.common.primitives.UnsignedLongs;

public final class UUIDType extends AbstractType<UUID>
{
    public static final UUIDType instance = new UUIDType();

    private UUIDType()
    {
        super(false);
    }

    public TypeSerializer<UUID> getSerializer()
    {
        return UUIDSerializer.instance;
    }

    protected int compareCustom(ByteBuffer b1, ByteBuffer b2)
    {
        int s1 = b1.position(), s2 = b2.position();
        int l1 = b1.limit(), l2 = b2.limit();

        boolean p1 = l1 - s1 == 16, p2 = l2 - s2 == 16;
        if (!(p1 & p2))
        {
            assert p1 | (l1 == s1);
            assert p2 | (l2 == s2);
            return p1 ? 1 : p2 ? -1 : 0;
        }

        long msb1 = b1.getLong(s1);
        long msb2 = b2.getLong(s2);

        int version1 = (int) ((msb1 >>> 12) & 0xf);
        int version2 = (int) ((msb2 >>> 12) & 0xf);
        if (version1 != version2)
            return version1 - version2;

        if (version1 == 1)
        {
            long reorder1 = TimeUUIDType.reorderTimestampBytes(msb1);
            long reorder2 = TimeUUIDType.reorderTimestampBytes(msb2);
            int c = Long.compare(reorder1, reorder2);
            if (c != 0)
                return c;
        }
        else
        {
            int c = UnsignedLongs.compare(msb1, msb2);
            if (c != 0)
                return c;
        }

        return UnsignedLongs.compare(b1.getLong(s1 + 8), b2.getLong(s2 + 8));
    }
}
