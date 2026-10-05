package org.apache.cassandra.stress.util;

import java.util.concurrent.TimeUnit;

public final class Sleep {
    private Sleep() {}

    public static void uninterruptibly(long duration, TimeUnit unit) {
        boolean interrupted = false;
        long remaining = unit.toNanos(duration);
        long end = System.nanoTime() + remaining;
        try {
            while (true) {
                try {
                    TimeUnit.NANOSECONDS.sleep(remaining);
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                    remaining = end - System.nanoTime();
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
