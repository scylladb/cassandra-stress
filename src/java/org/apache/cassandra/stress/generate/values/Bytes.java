// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.cassandra.stress.generate.FasterRandom;
import org.apache.cassandra.stress.marshal.BytesType;

public class Bytes extends Generator<ByteBuffer> {
    private final byte[] bytes;
    private final FasterRandom rand = new FasterRandom();

    public Bytes(String name, GeneratorConfig config) {
        super(BytesType.instance, config, name, ByteBuffer.class);
        bytes = new byte[(int) sizeDistribution.maxValue()];
    }

    @Override
    public ByteBuffer generate() {
        long seed = identityDistribution.next();
        sizeDistribution.setSeed(seed);
        rand.setSeed(~seed);
        int size = (int) sizeDistribution.next();
        for (int i = 0; i < size; )
            for (long v = rand.nextLong(), n = Math.min(size - i, Long.SIZE / Byte.SIZE); n-- > 0; v >>= Byte.SIZE)
                bytes[i++] = (byte) v;
        return ByteBuffer.wrap(Arrays.copyOf(bytes, size));
    }
}
