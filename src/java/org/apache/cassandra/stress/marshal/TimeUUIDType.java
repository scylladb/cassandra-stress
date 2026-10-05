// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class TimeUUIDType extends AbstractType<UUID> {
    public static final TimeUUIDType instance = new TimeUUIDType();

    private TimeUUIDType() {
        super(false);
    }

    @Override
    public TypeSerializer<UUID> getSerializer() {
        return TimeUUIDSerializer.instance;
    }

    @Override
    protected int compareCustom(ByteBuffer b1, ByteBuffer b2) {
        int s1 = b1.position();
        int s2 = b2.position();
        int l1 = b1.limit();
        int l2 = b2.limit();

        boolean p1 = l1 - s1 == 16;
        boolean p2 = l2 - s2 == 16;
        if (!(p1 && p2)) {
            assert p1 || (l1 == s1);
            assert p2 || (l2 == s2);
            return p1 ? 1 : p2 ? -1 : 0;
        }

        long msb1 = b1.getLong(s1);
        long msb2 = b2.getLong(s2);
        msb1 = reorderTimestampBytes(msb1);
        msb2 = reorderTimestampBytes(msb2);

        assert (msb1 & topbyte(0xf0L)) == topbyte(0x10L);
        assert (msb2 & topbyte(0xf0L)) == topbyte(0x10L);

        int c = Long.compare(msb1, msb2);
        if (c != 0) return c;

        long lsb1 = signedBytesToNativeLong(b1.getLong(s1 + 8));
        long lsb2 = signedBytesToNativeLong(b2.getLong(s2 + 8));
        return Long.compare(lsb1, lsb2);
    }

    private static long signedBytesToNativeLong(long signedBytes) {
        return signedBytes ^ 0x0080808080808080L;
    }

    private static long topbyte(long topbyte) {
        return topbyte << 56;
    }

    static long reorderTimestampBytes(long input) {
        return (input << 48) | ((input << 16) & 0xFFFF00000000L) | (input >>> 32);
    }
}
