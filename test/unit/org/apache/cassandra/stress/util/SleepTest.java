package org.apache.cassandra.stress.util;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SleepTest
{
    @Test
    void sleepsTheFullTimeAndKeepsTheInterrupt()
    {
        Thread.currentThread().interrupt();
        long start = System.nanoTime();
        Sleep.uninterruptibly(50, TimeUnit.MILLISECONDS);
        long elapsed = System.nanoTime() - start;
        assertTrue(Thread.interrupted());
        assertTrue(elapsed >= TimeUnit.MILLISECONDS.toNanos(50), "slept " + elapsed + " ns");
    }
}
