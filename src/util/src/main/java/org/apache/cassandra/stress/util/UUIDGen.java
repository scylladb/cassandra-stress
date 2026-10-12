// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UUIDGen {
    private static final long START_EPOCH = -12219292800000L;

    private UUIDGen() {}

    public static UUID getTimeUUID(long when, long nanos, long clockSeqAndNode) {
        return new UUID(createTime(fromUnixTimestamp(when, nanos)), clockSeqAndNode);
    }

    public static UUID getUUID(ByteBuffer raw) {
        return new UUID(raw.getLong(raw.position()), raw.getLong(raw.position() + 8));
    }

    public static ByteBuffer toByteBuffer(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        buffer.flip();
        return buffer;
    }

    private static long fromUnixTimestamp(long timestamp, long nanos) {
        return ((timestamp - START_EPOCH) * 10000) + nanos;
    }

    private static long createTime(long nanosSince) {
        long msb = 0L;
        msb |= (0x00000000ffffffffL & nanosSince) << 32;
        msb |= (0x0000ffff00000000L & nanosSince) >>> 16;
        msb |= (0xffff000000000000L & nanosSince) >>> 48;
        msb |= 0x0000000000001000L;
        return msb;
    }
}
