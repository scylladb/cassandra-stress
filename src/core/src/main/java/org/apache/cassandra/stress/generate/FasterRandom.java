// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import java.util.Random;
import org.apache.commons.math3.random.RandomGenerator;

public class FasterRandom implements RandomGenerator {
    // The multiplier, the increment and the 48-bit mask of the generator of java.util.Random.
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long INCREMENT = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    private long seed;
    private long rolloverSeed;
    private int reseed;
    private Random gaussian;

    @Override
    public void setSeed(int seed) {
        setSeed((long) seed);
    }

    @Override
    public void setSeed(int[] ints) {
        if (ints.length > 1) {
            setSeed(((long) ints[0] << 32) | ints[1]);
        } else {
            setSeed(ints[0]);
        }
    }

    @Override
    public void setSeed(long seed) {
        this.seed = seed;
        rollover();
    }

    // Gives the value of new Random(seed).nextLong(). Bytes and Strings seed once for each value, and
    // java.util.Random keeps its state in an AtomicLong, so this arithmetic on a plain long is faster.
    private void rollover() {
        this.reseed = 0;
        rolloverSeed = seed;
        gaussian = null;
        long state = (seed ^ MULTIPLIER) & MASK;
        state = (state * MULTIPLIER + INCREMENT) & MASK;
        long high = (long) (int) (state >>> 16) << 32;
        state = (state * MULTIPLIER + INCREMENT) & MASK;
        seed = high + (int) (state >>> 16);
    }

    @Override
    public void nextBytes(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            long next = nextLong();
            while (i < bytes.length) {
                bytes[i++] = (byte) (next & 0xFF);
                next >>>= 8;
            }
        }
    }

    @Override
    public int nextInt() {
        return (int) nextLong();
    }

    @Override
    public int nextInt(int i) {
        return Math.abs((int) nextLong() % i);
    }

    @Override
    public long nextLong() {
        if (++this.reseed == 32) {
            rollover();
        }

        long seed = this.seed;
        seed ^= seed >> 12;
        seed ^= seed << 25;
        seed ^= seed >> 27;
        this.seed = seed;
        return seed * 2685821657736338717L;
    }

    @Override
    public boolean nextBoolean() {
        return ((int) nextLong() & 1) == 1;
    }

    @Override
    public float nextFloat() {
        return Float.intBitsToFloat((int) nextLong());
    }

    @Override
    public double nextDouble() {
        return Double.longBitsToDouble(nextLong());
    }

    // The values of a java.util.Random that has the seed of the last rollover and gave one long.
    @Override
    public double nextGaussian() {
        if (gaussian == null) {
            gaussian = new Random(rolloverSeed);
            gaussian.nextLong();
        }
        return gaussian.nextGaussian();
    }
}
