// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import org.junit.Test;

import org.apache.cassandra.stress.settings.OptionDistribution;

import static org.junit.Assert.assertEquals;

public class DistributionSequenceTest
{
    private static Distribution sequence(String spec)
    {
        return OptionDistribution.get(spec).get();
    }

    private static void assertNextValues(Distribution dist, long... expected)
    {
        for (long value : expected)
            assertEquals(value, dist.next());
    }

    @Test
    public void countsUpAndWraps()
    {
        Distribution dist = sequence("seq(1..10)");
        assertEquals(1, dist.minValue());
        assertEquals(10, dist.maxValue());
        assertEquals(5, dist.average());
        assertEquals(1, dist.inverseCumProb(0d));
        assertEquals(10, dist.inverseCumProb(1d));
        assertNextValues(dist, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1);
    }

    @Test
    public void countsUpThroughNegativeValues()
    {
        Distribution dist = sequence("seq(-1000..-10)");
        assertEquals(-1000, dist.minValue());
        assertEquals(-10, dist.maxValue());
        assertEquals(-504, dist.average());

        long previous = dist.next();
        assertEquals(-1000, previous);
        for (long expected = -999; expected <= -10; expected++)
        {
            previous = dist.next();
            assertEquals(expected, previous);
        }
        assertEquals(-1000, dist.next());
    }

    @Test
    public void supportsTheFullLongRange()
    {
        Distribution dist = sequence(String.format("seq(1..%d)", Long.MAX_VALUE));
        assertEquals(1, dist.minValue());
        assertEquals(Long.MAX_VALUE, dist.maxValue());
        assertEquals(1, dist.inverseCumProb(0d));
        assertEquals(Long.MAX_VALUE, dist.inverseCumProb(1d));
    }

    @Test
    public void seedKeepsTheBoundsAndTheStep()
    {
        Distribution dist = sequence("seq(1..10)");
        for (int seed = 1; seed < 500; seed += seed)
        {
            dist.setSeed(seed);
            assertEquals(1, dist.minValue());
            assertEquals(10, dist.maxValue());

            long previous = dist.next();
            for (int i = 0; i < 9; i++)
            {
                long next = dist.next();
                assertEquals(previous == 10 ? 1 : previous + 1, next);
                previous = next;
            }
        }
    }
}
