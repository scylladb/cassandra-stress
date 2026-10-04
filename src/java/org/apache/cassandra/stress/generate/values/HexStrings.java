// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.UTF8Type;

public class HexStrings extends Generator<String>
{
    private final char[] chars;

    public HexStrings(String name, GeneratorConfig config)
    {
        super(UTF8Type.instance, config, name, String.class);
        chars = new char[(int) sizeDistribution.maxValue()];
    }

    @Override
    public String generate()
    {
        long seed = identityDistribution.next();
        sizeDistribution.setSeed(seed);
        int size = (int) sizeDistribution.next();
        for (int i = 0 ; i < size ; i +=16)
        {
            long value = identityDistribution.next();
            for (int j = 0 ; j < 16 && i + j < size ; j++)
            {
                int v = (int) (value & 15);
                chars[i + j] = (char) ((v < 10 ? '0' : 'A') + v);
                value >>>= 4;
            }
        }
        return new String(chars, 0, size);
    }
}
