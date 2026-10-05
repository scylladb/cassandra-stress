// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.apache.cassandra.stress.operations.OpDistribution;
import org.apache.cassandra.stress.operations.OpDistributionFactory;
import org.apache.cassandra.stress.report.StressMetrics;
import org.apache.cassandra.stress.settings.SettingsCommand;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.JavaDriverClient;
import org.apache.cassandra.stress.util.ResultLogger;
import org.apache.cassandra.stress.util.Sleep;
import org.jctools.queues.atomic.SpscAtomicArrayQueue;
import org.jctools.queues.atomic.SpscUnboundedAtomicArrayQueue;

public class StressAction implements Runnable {

    private final StressSettings settings;
    private final ResultLogger output;

    public StressAction(StressSettings settings, ResultLogger out) {
        this.settings = settings;
        output = out;
    }

    @Override
    public void run() {
        settings.maybeCreateKeyspaces();

        if (settings.command.count == 0) {
            output.println("N=0: SCHEMA CREATED, NOTHING ELSE DONE.");
            settings.disconnect();
            return;
        }

        output.println("Sleeping 2s...");
        Sleep.uninterruptibly(2, TimeUnit.SECONDS);

        if (!settings.command.noWarmup) warmup(settings.command.getFactory(settings));

        if ((settings.command.truncate == SettingsCommand.TruncateWhen.ONCE)
                || ((settings.rate.threadCount != -1)
                        && (settings.command.truncate == SettingsCommand.TruncateWhen.ALWAYS)))
            settings.command.truncateTables(settings);

        if (settings.rate.threadCount == -1) output.println("Thread count was not specified");

        UniformRateLimiter rateLimiter = null;
        if (settings.rate.opsPerSecond > 0) rateLimiter = new UniformRateLimiter(settings.rate.opsPerSecond);

        boolean success;
        if (settings.rate.minThreads > 0) success = runMulti(settings.rate.auto, rateLimiter);
        else
            success = null
                    != run(
                            settings.command.getFactory(settings),
                            settings.rate.threadCount,
                            settings.command.count,
                            settings.command.duration,
                            rateLimiter,
                            settings.command.durationUnits,
                            output,
                            false);

        if (success) output.println("END");
        else output.println("FAILURE");

        settings.disconnect();

        if (!success) throw new RuntimeException("Failed to execute stress action");
    }

    @SuppressWarnings("resource")
    private void warmup(OpDistributionFactory operations) {
        int iterations = (settings.command.count >= 0 ? Math.min(50000, (int) (settings.command.count * 0.25)) : 50000)
                * settings.node.nodes.size();
        if (iterations <= 0) return;

        int threads = 100;

        if (settings.rate.maxThreads > 0) threads = Math.min(threads, settings.rate.maxThreads);
        if (settings.rate.threadCount > 0) threads = Math.min(threads, settings.rate.threadCount);

        for (OpDistributionFactory single : operations.each()) {
            output.println(String.format("Warming up %s with %d iterations...", single.desc(), iterations));
            boolean success = null != run(single, threads, iterations, 0, null, null, ResultLogger.NOOP, true);
            if (!success) throw new RuntimeException("Failed to execute warmup");
        }
    }

    private boolean runMulti(boolean auto, UniformRateLimiter rateLimiter) {
        if (settings.command.targetUncertainty >= 0)
            output.println(
                    "WARNING: uncertainty mode (err<) results in uneven workload between thread runs, so should be"
                            + " used for high level analysis only");
        int prevThreadCount = -1;
        int threadCount = settings.rate.minThreads;
        List<StressMetrics> results = new ArrayList<>();
        List<String> runIds = new ArrayList<>();
        do {
            output.println("");
            output.println(String.format("Running with %d threadCount", threadCount));

            if (settings.command.truncate == SettingsCommand.TruncateWhen.ALWAYS)
                settings.command.truncateTables(settings);

            StressMetrics result = run(
                    settings.command.getFactory(settings),
                    threadCount,
                    settings.command.count,
                    settings.command.duration,
                    rateLimiter,
                    settings.command.durationUnits,
                    output,
                    false);
            if (result == null) return false;
            results.add(result);

            if (prevThreadCount > 0)
                output.println(String.format(
                        "Improvement over %d threadCount: %.0f%%",
                        prevThreadCount, 100 * averageImprovement(results, 1)));

            runIds.add(threadCount + " threadCount");
            prevThreadCount = threadCount;
            if (threadCount < 500) threadCount += 100;
            else if (threadCount < 1500) threadCount = (int) (threadCount * 1.2);
            else threadCount = (int) (threadCount * 1.1);

            if (!results.isEmpty() && threadCount > settings.rate.maxThreads) break;

            if (settings.command.type.updates) {
                output.println("Sleeping for 15s");
                try {
                    Thread.sleep(15 * 1000);
                } catch (InterruptedException e) {
                    return false;
                }
            }
        } while (!auto
                || (hasAverageImprovement(results, 3, 0)
                        && hasAverageImprovement(results, 5, settings.command.targetUncertainty)));

        StressMetrics.summarise(runIds, results, output);
        return true;
    }

    private boolean hasAverageImprovement(List<StressMetrics> results, int count, double minImprovement) {
        return results.size() < count + 1 || averageImprovement(results, count) >= minImprovement;
    }

    private double averageImprovement(List<StressMetrics> results, int count) {
        double improvement = 0;
        for (int i = results.size() - count; i < results.size(); i++) {
            double prev = results.get(i - 1).opRate();
            double cur = results.get(i).opRate();
            improvement += (cur - prev) / prev;
        }
        return improvement / count;
    }

    @SuppressWarnings("EmptyCatch")
    private StressMetrics run(
            OpDistributionFactory operations,
            int threadCount,
            long opCount,
            long duration,
            UniformRateLimiter rateLimiter,
            TimeUnit durationUnits,
            ResultLogger output,
            boolean isWarmup) {
        output.println(String.format(
                "Running %s with %d threads %s",
                operations.desc(),
                threadCount,
                durationUnits != null
                        ? duration + " " + durationUnits.toString().toLowerCase(Locale.ROOT)
                        : opCount > 0
                                ? "for " + opCount + " iteration"
                                : "until stderr of mean < " + settings.command.targetUncertainty));
        final WorkManager workManager;
        if (opCount < 0) workManager = new WorkManager.ContinuousWorkManager();
        else workManager = new WorkManager.FixedWorkManager(opCount);

        final StressMetrics metrics = new StressMetrics(output, settings.log.intervalMillis, settings);

        final CountDownLatch anyFailed = new CountDownLatch(1);
        final CountDownLatch releaseConsumers = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final CountDownLatch start = new CountDownLatch(threadCount);
        final Consumer[] consumers = new Consumer[threadCount];
        for (int i = 0; i < threadCount; i++) {
            consumers[i] = new Consumer(
                    operations, isWarmup, done, start, releaseConsumers, anyFailed, workManager, metrics, rateLimiter);
        }

        for (int i = 0; i < threadCount; i++) consumers[i].start();

        try {
            start.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interruption", e);
        }
        if (rateLimiter != null) {
            rateLimiter.start();
        }
        releaseConsumers.countDown();

        metrics.start();

        if (durationUnits != null) {
            try {
                if (settings.errors.failFast) {
                    anyFailed.await(duration, durationUnits);
                } else {
                    done.await(duration, durationUnits);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            workManager.stop();
        } else if (opCount <= 0) {
            try {
                metrics.waitUntilConverges(
                        settings.command.targetUncertainty,
                        settings.command.minimumUncertaintyMeasurements,
                        settings.command.maximumUncertaintyMeasurements);
            } catch (InterruptedException ignored) {
            }
            workManager.stop();
        }

        try {
            done.await();
            metrics.stop();
        } catch (InterruptedException ignored) {
        }

        if (metrics.wasCancelled()) return null;

        metrics.summarise();

        boolean success = true;
        for (Consumer consumer : consumers) success &= consumer.success;

        if (!success) return null;

        return metrics;
    }

    private static class UniformRateLimiter {
        volatile long start = Long.MIN_VALUE;
        final long intervalNs;
        final AtomicLong opIndex = new AtomicLong();

        UniformRateLimiter(int opsPerSec) {
            intervalNs = 1000000000 / opsPerSec;
        }

        void start() {
            start = System.nanoTime();
        }

        long acquire(int partitionCount) {
            long currOpIndex = opIndex.getAndAdd(partitionCount);
            return start + currOpIndex * intervalNs;
        }
    }

    private static class StreamOfOperations {
        private final OpDistribution operations;
        private final UniformRateLimiter rateLimiter;
        private final WorkManager workManager;

        StreamOfOperations(OpDistribution operations, UniformRateLimiter rateLimiter, WorkManager workManager) {
            this.operations = operations;
            this.rateLimiter = rateLimiter;
            this.workManager = workManager;
        }

        Operation nextOp() {
            Operation op = operations.next();
            final int partitionCount = op.ready(workManager);
            if (partitionCount == 0) return null;
            if (rateLimiter != null) {
                long intendedTime = rateLimiter.acquire(partitionCount);
                op.intendedStartNs(intendedTime);
                long now;
                while ((now = System.nanoTime()) < intendedTime) {
                    LockSupport.parkNanos(intendedTime - now);
                }
            }
            return op;
        }

        void abort() {
            workManager.stop();
        }
    }

    public static class OpMeasurement {
        public String opType;
        public long intended;
        public long started;
        public long ended;
        public long rowCnt;
        public long partitionCnt;
        public boolean err;

        @Override
        public String toString() {
            return "OpMeasurement [opType=" + opType + ", intended=" + intended + ", started=" + started + ", ended="
                    + ended + ", rowCnt=" + rowCnt + ", partitionCnt=" + partitionCnt + ", err=" + err + "]";
        }
    }

    @FunctionalInterface
    public interface MeasurementSink {
        void record(
                String opType, long intended, long started, long ended, long rowCnt, long partitionCnt, boolean err);
    }

    public class Consumer extends Thread implements MeasurementSink {
        private final StreamOfOperations opStream;
        private final StressMetrics metrics;
        private volatile boolean success = true;
        private final CountDownLatch done;
        private final CountDownLatch start;
        private final CountDownLatch releaseConsumers;
        private final CountDownLatch anyFailed;
        public final Queue<OpMeasurement> measurementsRecycling;
        public final Queue<OpMeasurement> measurementsReporting;

        public Consumer(
                OpDistributionFactory operations,
                boolean isWarmup,
                CountDownLatch done,
                CountDownLatch start,
                CountDownLatch releaseConsumers,
                CountDownLatch anyFailed,
                WorkManager workManager,
                StressMetrics metrics,
                UniformRateLimiter rateLimiter) {
            OpDistribution opDistribution = operations.get(isWarmup, this);
            this.done = done;
            this.start = start;
            this.releaseConsumers = releaseConsumers;
            this.anyFailed = anyFailed;
            this.metrics = metrics;
            this.opStream = new StreamOfOperations(opDistribution, rateLimiter, workManager);
            this.measurementsRecycling = new SpscAtomicArrayQueue<OpMeasurement>(8 * 1024);
            this.measurementsReporting = new SpscUnboundedAtomicArrayQueue<OpMeasurement>(2048);
            metrics.add(this);
        }

        @SuppressWarnings({"EmptyCatch", "PMD.UnusedAssignment"})
        @Override
        public void run() {
            try {
                JavaDriverClient client;
                try {
                    client = settings.getJavaDriverClient();
                } finally {
                    start.countDown();
                }

                releaseConsumers.await();

                while (true) {
                    if (settings.errors.failFast && anyFailed.getCount() == 0) {
                        success = false;
                        break;
                    }
                    Operation op = opStream.nextOp();
                    if (op == null) break;

                    try {
                        op.run(client);
                    } catch (NoSuchElementException ignored) {
                    } catch (Exception e) {
                        if (output == null) System.err.println(e.getMessage());
                        else output.printException(e);

                        success = false;
                        anyFailed.countDown();
                        opStream.abort();
                        metrics.cancel();
                        return;
                    }
                }
            } catch (java.lang.Error e) {
                e.printStackTrace();
                System.err.println(e.getMessage());
                success = false;
                anyFailed.countDown();
            } catch (Exception e) {
                System.err.println(e.getMessage());
                success = false;
                anyFailed.countDown();
            } finally {
                done.countDown();
            }
        }

        @Override
        public void record(
                String opType, long intended, long started, long ended, long rowCnt, long partitionCnt, boolean err) {
            OpMeasurement opMeasurement = measurementsRecycling.poll();
            if (opMeasurement == null) {
                opMeasurement = new OpMeasurement();
            }
            opMeasurement.opType = opType;
            opMeasurement.intended = intended;
            opMeasurement.started = started;
            opMeasurement.ended = ended;
            opMeasurement.rowCnt = rowCnt;
            opMeasurement.partitionCnt = partitionCnt;
            opMeasurement.err = err;
            measurementsReporting.offer(opMeasurement);
        }
    }
}
