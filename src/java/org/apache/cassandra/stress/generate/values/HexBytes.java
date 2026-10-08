// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.cassandra.stress.marshal.BytesType;

public class HexBytes extends Generator<ByteBuffer> {
    private final byte[] bytes;

    public HexBytes(String name, GeneratorConfig config) {
        super(BytesType.instance, config, name, ByteBuffer.class);
        bytes = new byte[(int) sizeDistribution.maxValue()];
    }

    @Override
    public ByteBuffer generate() {
        long seed = identityDistribution.next();
        sizeDistribution.setSeed(seed);
        int size = (int) sizeDistribution.next();
        for (int i = 0; i < size; i += 16) {
            long value = identityDistribution.next();
            for (int j = 0; j < 16 && i + j < size; j++) {
                int v = (int) (value & 15);
                bytes[i + j] = (byte) ((v < 10 ? '0' : 'A') + v);
                value >>>= 4;
            }
        }
        return ByteBuffer.wrap(Arrays.copyOf(bytes, size));
    }
}
