// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.UTF8Type;
import org.apache.cassandra.stress.generate.FasterRandom;

public class Strings extends Generator<String>
{
    private final char[] chars;
    private final FasterRandom rnd = new FasterRandom();

    public Strings(String name, GeneratorConfig config)
    {
        super(UTF8Type.instance, config, name, String.class);
        chars = new char[(int) sizeDistribution.maxValue()];
    }

    @Override
    public String generate()
    {
        long seed = identityDistribution.next();
        sizeDistribution.setSeed(seed);
        rnd.setSeed(~seed);
        int size = (int) sizeDistribution.next();
        for (int i = 0; i < size; )
            for (long v = rnd.nextLong(),
                 n = Math.min(size - i, Long.SIZE/Byte.SIZE);
                 n-- > 0; v >>= Byte.SIZE)
                chars[i++] = (char) (((v & 127) + 32) & 127);
        return new String(chars, 0, size);
    }
}
