package org.apache.cassandra.stress.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UncertaintyTest
{
    @Test
    void identicalMeasurementsHaveNoUncertainty()
    {
        Uncertainty uncertainty = new Uncertainty();
        for (int i = 0; i < 10; i++)
            uncertainty.update(5.0);
        assertEquals(0.0, uncertainty.getUncertainty(), 1e-9);
    }

    @Test
    void computesStandardErrorOverMean()
    {
        Uncertainty uncertainty = new Uncertainty();
        uncertainty.update(1);
        uncertainty.update(3);
        assertEquals((1.0 / Math.sqrt(2)) / 2.0, uncertainty.getUncertainty(), 1e-9);
    }

    @Test
    void wakesWaitersAtTheMaximumMeasurementCount() throws Exception
    {
        Uncertainty uncertainty = new Uncertainty();
        CompletableFuture<Void> waiter = CompletableFuture.runAsync(() -> {
            try
            {
                uncertainty.await(0.0, 1, 3);
            }
            catch (InterruptedException e)
            {
                throw new IllegalStateException(e);
            }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!waiter.isDone() && System.nanoTime() < deadline)
        {
            uncertainty.update(1);
            uncertainty.update(100);
            Thread.onSpinWait();
        }
        waiter.get(1, TimeUnit.SECONDS);
    }

    @Test
    void wakeAllReleasesWaiters() throws Exception
    {
        Uncertainty uncertainty = new Uncertainty();
        CompletableFuture<Void> waiter = CompletableFuture.runAsync(() -> {
            try
            {
                uncertainty.await(0.0, 1_000, 1_000);
            }
            catch (InterruptedException e)
            {
                throw new IllegalStateException(e);
            }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!waiter.isDone() && System.nanoTime() < deadline)
        {
            uncertainty.wakeAll();
            Thread.onSpinWait();
        }
        waiter.get(1, TimeUnit.SECONDS);
    }
}
