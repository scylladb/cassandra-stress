package org.apache.cassandra.stress.generate;

import org.junit.jupiter.api.Test;

import org.apache.cassandra.stress.settings.OptionDistribution;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistributionSequenceTest
{
    private static Distribution sequence(String spec)
    {
        return OptionDistribution.get(spec).get();
    }

    @Test
    void countsUpAndWraps()
    {
        Distribution dist = sequence("seq(1..10)");
        assertEquals(1, dist.minValue());
        assertEquals(10, dist.maxValue());
        assertEquals(5, dist.average());
        assertEquals(1, dist.inverseCumProb(0d));
        assertEquals(10, dist.inverseCumProb(1d));
        for (long expected : new long[]{ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1 })
            assertEquals(expected, dist.next());
    }

    @Test
    void countsUpThroughNegativeValues()
    {
        Distribution dist = sequence("seq(-1000..-10)");
        assertEquals(-1000, dist.minValue());
        assertEquals(-10, dist.maxValue());
        assertEquals(-504, dist.average());
        for (long expected = -1000; expected <= -10; expected++)
            assertEquals(expected, dist.next());
        assertEquals(-1000, dist.next());
    }

    @Test
    void supportsTheFullLongRange()
    {
        Distribution dist = sequence("seq(1..%d)".formatted(Long.MAX_VALUE));
        assertEquals(1, dist.minValue());
        assertEquals(Long.MAX_VALUE, dist.maxValue());
        assertEquals(1, dist.inverseCumProb(0d));
        assertEquals(Long.MAX_VALUE, dist.inverseCumProb(1d));
    }

    @Test
    void seedKeepsTheBoundsAndTheStep()
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
