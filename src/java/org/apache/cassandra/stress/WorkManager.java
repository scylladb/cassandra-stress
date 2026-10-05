// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.util.concurrent.atomic.AtomicLong;

public interface WorkManager {
    int takePermits(int count);

    void stop();

    final class FixedWorkManager implements WorkManager {

        final AtomicLong permits;

        public FixedWorkManager(long permits) {
            this.permits = new AtomicLong(permits);
        }

        @Override
        public int takePermits(int count) {
            while (true) {
                long cur = permits.get();
                if (cur == 0) return -1;
                count = (int) Math.min(count, cur);
                long next = cur - count;
                if (permits.compareAndSet(cur, next)) return count;
            }
        }

        @Override
        public void stop() {
            permits.getAndSet(0);
        }
    }

    final class ContinuousWorkManager implements WorkManager {

        volatile boolean stop;

        @Override
        public int takePermits(int count) {
            if (stop) return -1;
            return count;
        }

        @Override
        public void stop() {
            stop = true;
        }
    }
}
