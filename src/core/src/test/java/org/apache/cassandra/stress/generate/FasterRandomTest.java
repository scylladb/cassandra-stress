package org.apache.cassandra.stress.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class FasterRandomTest {
    /** FasterRandom with a java.util.Random for each rollover, the generator that the data of stress comes from. */
    private static final class Reference {
        final Random random = new Random();
        long seed;
        int reseed;

        void setSeed(long seed) {
            this.seed = seed;
            rollover();
        }

        void rollover() {
            reseed = 0;
            random.setSeed(seed);
            seed = random.nextLong();
        }

        long nextLong() {
            if (++reseed == 32) {
                rollover();
            }
            long s = seed;
            s ^= s >> 12;
            s ^= s << 25;
            s ^= s >> 27;
            seed = s;
            return s * 2685821657736338717L;
        }
    }

    @Test
    void givesTheValuesOfTheJavaUtilRandomRollover() {
        SplittableRandom seeds = new SplittableRandom(42);
        FasterRandom faster = new FasterRandom();
        Reference reference = new Reference();
        for (int i = 0; i < 10_000; i++) {
            long seed = i < 4 ? new long[] {0, -1, Long.MIN_VALUE, Long.MAX_VALUE}[i] : seeds.nextLong();
            faster.setSeed(seed);
            reference.setSeed(seed);
            for (int n = 0; n < 100; n++) {
                assertEquals(reference.nextLong(), faster.nextLong(), "seed " + seed + ", value " + n);
            }
        }
    }

    @Test
    void givesTheGaussiansOfTheJavaUtilRandomRollover() {
        FasterRandom faster = new FasterRandom();
        Reference reference = new Reference();
        for (long seed = -500; seed < 500; seed += 7) {
            faster.setSeed(seed);
            reference.setSeed(seed);
            for (int n = 0; n < 70; n++) {
                if (n % 5 == 0) {
                    assertEquals(
                            reference.random.nextGaussian(), faster.nextGaussian(), "seed " + seed + ", value " + n);
                }
                assertEquals(reference.nextLong(), faster.nextLong());
            }
        }
    }
}
