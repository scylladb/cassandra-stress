package org.apache.cassandra.stress.generate;

import org.junit.Test;

import org.apache.cassandra.stress.settings.OptionDistribution;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DistributionBoundApacheTest
{
    private static Distribution uniform()
    {
        return OptionDistribution.get("uniform(1..1000000000)").get();
    }

    @Test
    public void sampleAfterSeedDependsOnlyOnTheLastSeed()
    {
        Distribution reseededTwice = uniform();
        Distribution reseededOnce = uniform();
        assertTrue(reseededTwice instanceof DistributionBoundApache);

        for (long seed = 1; seed < 1000; seed += 7)
        {
            reseededTwice.setSeed(seed * 13);
            reseededTwice.setSeed(seed);
            reseededOnce.setSeed(seed);
            assertEquals(reseededOnce.next(), reseededTwice.next());
            assertEquals(reseededOnce.next(), reseededTwice.next());
        }
    }

    @Test
    public void offsetSampleAfterSeedDependsOnlyOnTheLastSeed()
    {
        Distribution reseededTwice = OptionDistribution.get("exp(1..1000000)").get();
        Distribution reseededOnce = OptionDistribution.get("exp(1..1000000)").get();
        assertTrue(reseededTwice instanceof DistributionOffsetApache);

        for (long seed = 1; seed < 1000; seed += 7)
        {
            reseededTwice.setSeed(seed * 13);
            reseededTwice.setSeed(seed);
            reseededOnce.setSeed(seed);
            assertEquals(reseededOnce.next(), reseededTwice.next());
        }
    }

    @Test
    public void sampleMatchesTheDelegateSeededDirectly()
    {
        DistributionBoundApache lazy = (DistributionBoundApache) uniform();
        DistributionBoundApache direct = (DistributionBoundApache) uniform();
        for (long seed = 1; seed < 1000; seed += 7)
        {
            lazy.setSeed(seed);
            direct.delegate.reseedRandomGenerator(seed);
            assertEquals((long) direct.delegate.sample(), lazy.next());
        }
    }
}
