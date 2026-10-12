package org.apache.cassandra.stress.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.cassandra.stress.settings.OptionDistribution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DistributionBoundApacheTest {
    private static Distribution distribution(String spec) {
        return OptionDistribution.get(spec).get();
    }

    @ParameterizedTest
    @ValueSource(strings = {"uniform(1..1000000000)", "gauss(1..1000000)", "exp(1..1000000)", "extreme(1..1000000,2)"})
    void sampleAfterSeedDependsOnlyOnTheLastSeed(String spec) {
        Distribution reseededTwice = distribution(spec);
        Distribution reseededOnce = distribution(spec);
        for (long seed = 1; seed < 1000; seed += 7) {
            reseededTwice.setSeed(seed * 13);
            reseededTwice.setSeed(seed);
            reseededOnce.setSeed(seed);
            assertEquals(reseededOnce.next(), reseededTwice.next());
            assertEquals(reseededOnce.next(), reseededTwice.next());
        }
    }

    @Test
    void uniformAndExponentialUseTheApacheAdapters() {
        assertInstanceOf(DistributionBoundApache.class, distribution("uniform(1..1000000000)"));
        assertInstanceOf(DistributionOffsetApache.class, distribution("exp(1..1000000)"));
    }

    @Test
    void sampleMatchesTheDelegateSeededDirectly() {
        DistributionBoundApache lazy = (DistributionBoundApache) distribution("uniform(1..1000000000)");
        DistributionBoundApache direct = (DistributionBoundApache) distribution("uniform(1..1000000000)");
        for (long seed = 1; seed < 1000; seed += 7) {
            lazy.setSeed(seed);
            direct.delegate.reseedRandomGenerator(seed);
            assertEquals((long) direct.delegate.sample(), lazy.next());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"uniform(1..1000)", "gauss(1..1000)", "exp(1..1000)", "fixed(7)"})
    void samplesStayWithinTheBounds(String spec) {
        Distribution dist = distribution(spec);
        for (int i = 0; i < 10_000; i++) {
            long value = dist.next();
            assertTrue(value >= dist.minValue() && value <= dist.maxValue(), spec + " gave " + value);
        }
    }
}
