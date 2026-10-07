// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.report;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.HdrHistogram.Histogram;
import org.apache.cassandra.stress.StressAction.Consumer;
import org.apache.cassandra.stress.StressAction.MeasurementSink;
import org.apache.cassandra.stress.StressAction.OpMeasurement;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ResultLogger;
import org.apache.cassandra.stress.util.Uncertainty;

public class StressMetrics implements MeasurementSink {
    private final List<Consumer> consumers = new ArrayList<>();
    private final ResultLogger output;
    private final Thread thread;
    private final Uncertainty rowRateUncertainty = new Uncertainty();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final HdrLog hdrLog;
    private final long epochNs = System.nanoTime();
    private final long epochMs = System.currentTimeMillis();

    private volatile boolean stop;
    private volatile boolean cancelled;

    private final Map<String, TimingInterval> opTypeToCurrentTimingInterval = new TreeMap<>();
    private final Map<String, TimingInterval> opTypeToSummaryTimingInterval = new TreeMap<>();
    private final Queue<OpMeasurement> leftovers = new ArrayDeque<>();
    private final TimingInterval totalCurrentInterval;
    private final TimingInterval totalSummaryInterval;

    public StressMetrics(ResultLogger output, final long logIntervalMillis, StressSettings settings, HdrLog hdrLog) {
        this.output = output;
        this.hdrLog = hdrLog;
        this.totalCurrentInterval = new TimingInterval(settings.rate.isFixed);
        this.totalSummaryInterval = new TimingInterval(settings.rate.isFixed);
        printHeader("", output);
        thread = Thread.ofPlatform().name("StressMetrics").unstarted(() -> reportingLoop(logIntervalMillis));
    }

    public void start() {
        thread.start();
    }

    public void waitUntilConverges(double targetUncertainty, int minMeasurements, int maxMeasurements)
            throws InterruptedException {
        rowRateUncertainty.await(targetUncertainty, minMeasurements, maxMeasurements);
    }

    public void cancel() {
        cancelled = true;
        stop = true;
        thread.interrupt();
        rowRateUncertainty.wakeAll();
    }

    public void stop() throws InterruptedException {
        stop = true;
        thread.interrupt();
        stopped.await();
    }

    private void reportingLoop(final long logIntervalMillis) {
        final long currentTimeMs = System.currentTimeMillis();
        final long startTimeMs = currentTimeMs - (currentTimeMs % 1000);
        long reportingStartNs = (System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(currentTimeMs - startTimeMs));
        final long parkIntervalNs = TimeUnit.MILLISECONDS.toNanos(logIntervalMillis);
        try {
            while (!stop) {
                final long wakupTarget = reportingStartNs + parkIntervalNs;
                sleepUntil(wakupTarget);
                if (stop) {
                    break;
                }
                recordInterval(wakupTarget, parkIntervalNs);
                reportingStartNs += parkIntervalNs;
            }

            final long end = System.nanoTime();
            recordInterval(end, end - reportingStartNs);
        } catch (Exception e) {
            e.printStackTrace();
            cancel();
        } finally {
            rowRateUncertainty.wakeAll();
            stopped.countDown();
        }
    }

    private void sleepUntil(final long until) {
        long parkFor;
        while (!stop && (parkFor = until - System.nanoTime()) > 0) {
            LockSupport.parkNanos(parkFor);
        }
    }

    @Override
    public void record(
            String opType, long intended, long started, long ended, long rowCnt, long partitionCnt, boolean err) {
        TimingInterval current = opTypeToCurrentTimingInterval.computeIfAbsent(
                opType, k -> new TimingInterval(totalCurrentInterval.isFixed));
        record(current, intended, started, ended, rowCnt, partitionCnt, err);
    }

    private void record(
            TimingInterval t, long intended, long started, long ended, long rowCnt, long partitionCnt, boolean err) {
        t.rowCount += rowCnt;
        t.partitionCount += partitionCnt;
        if (err) t.errorCount++;
        if (intended != 0) {
            t.responseTime().recordValue(ended - intended);
            t.waitTime().recordValue(started - intended);
        }
        final long sTime = ended - started;
        t.serviceTime().recordValue(sTime);
    }

    private void recordInterval(long intervalEnd, long parkIntervalNs) {

        drainConsumerMeasurements(intervalEnd, parkIntervalNs);

        rowRateUncertainty.update(totalCurrentInterval.adjustedRowRate());
        if (totalCurrentInterval.operationCount() != 0) {
            final boolean logPerOpSummaryLine = opTypeToCurrentTimingInterval.size() > 1;

            for (Map.Entry<String, TimingInterval> type : opTypeToCurrentTimingInterval.entrySet()) {
                final String opName = type.getKey();
                final TimingInterval opInterval = type.getValue();
                if (logPerOpSummaryLine) {
                    printRow(
                            "",
                            opName,
                            opInterval,
                            opTypeToSummaryTimingInterval.get(opName),
                            rowRateUncertainty,
                            output);
                }
                logHistograms(opName, opInterval);
                opInterval.reset();
            }

            printRow("", "total", totalCurrentInterval, totalSummaryInterval, rowRateUncertainty, output);
            totalCurrentInterval.reset();
        }
    }

    private void drainConsumerMeasurements(long intervalEnd, long parkIntervalNs) {
        int leftoversSize = leftovers.size();
        for (int i = 0; i < leftoversSize; i++) {
            OpMeasurement last = leftovers.poll();
            if (last.ended <= intervalEnd) {
                record(last.opType, last.intended, last.started, last.ended, last.rowCnt, last.partitionCnt, last.err);
                consumers.get(i % consumers.size()).measurementsRecycling.offer(last);
            } else {
                leftovers.offer(last);
            }
        }
        for (Consumer c : consumers) {
            Queue<OpMeasurement> in = c.measurementsReporting;
            Queue<OpMeasurement> out = c.measurementsRecycling;
            OpMeasurement last;
            while ((last = in.poll()) != null) {
                if (last.ended > intervalEnd) {
                    leftovers.add(last);
                    break;
                }
                record(last.opType, last.intended, last.started, last.ended, last.rowCnt, last.partitionCnt, last.err);
                out.offer(last);
            }
        }
        for (Entry<String, TimingInterval> currPerOp : opTypeToCurrentTimingInterval.entrySet()) {
            currPerOp.getValue().endNanos(intervalEnd);
            currPerOp.getValue().startNanos(intervalEnd - parkIntervalNs);
            TimingInterval summaryPerOp = opTypeToSummaryTimingInterval.computeIfAbsent(
                    currPerOp.getKey(), k -> new TimingInterval(totalCurrentInterval.isFixed));
            summaryPerOp.add(currPerOp.getValue());
            totalCurrentInterval.add(currPerOp.getValue());
        }
        totalCurrentInterval.endNanos(intervalEnd);
        totalCurrentInterval.startNanos(intervalEnd - parkIntervalNs);

        totalSummaryInterval.add(totalCurrentInterval);
    }

    private void logHistograms(String opName, TimingInterval opInterval) {
        if (hdrLog == null) return;
        final long startNs = opInterval.startNanos();
        final long endNs = opInterval.endNanos();

        logHistogram(opName + "-st", startNs, endNs, opInterval.serviceTime());
        logHistogram(opName + "-rt", startNs, endNs, opInterval.responseTime());
        logHistogram(opName + "-wt", startNs, endNs, opInterval.waitTime());
    }

    private void logHistogram(String opName, final long startNs, final long endNs, final Histogram histogram) {
        if (histogram.getTotalCount() != 0) {
            histogram.setTag(opName);
            final long relativeStartNs = startNs - epochNs;
            final long startMs = (long) (1000 * ((epochMs + NANOSECONDS.toMillis(relativeStartNs)) / 1000.0));
            histogram.setStartTimeStamp(startMs);
            final long relativeEndNs = endNs - epochNs;
            final long endMs = (long) (1000 * ((epochMs + NANOSECONDS.toMillis(relativeEndNs)) / 1000.0));
            histogram.setEndTimeStamp(endMs);
            hdrLog.write(histogram);
        }
    }

    public static final String HEADFORMAT = "%-10s%10s,%8s,%8s,%8s,%8s,%8s,%8s,%8s,%8s,%8s,%7s,%9s,%7s";
    public static final String ROWFORMAT =
            "%-10s%10d,%8.0f,%8.0f,%8.0f,%8.1f,%8.1f,%8.1f,%8.1f,%8.1f,%8.1f,%7.1f,%9.5f,%7d";
    public static final List<String> HEADMETRICS = List.of(
            "type",
            "total ops",
            "op/s",
            "pk/s",
            "row/s",
            "mean",
            "med",
            ".95",
            ".99",
            ".999",
            "max",
            "time",
            "stderr",
            "errors");
    public static final String HEAD = String.format(HEADFORMAT, HEADMETRICS.toArray());

    private static void printHeader(String prefix, ResultLogger output) {
        output.println(prefix + HEAD);
    }

    private static void printRow(
            String prefix,
            String type,
            TimingInterval interval,
            TimingInterval total,
            Uncertainty opRateUncertainty,
            ResultLogger output) {
        output.println(prefix
                + String.format(
                        ROWFORMAT,
                        type + ",",
                        total.operationCount(),
                        interval.opRate(),
                        interval.partitionRate(),
                        interval.rowRate(),
                        interval.meanLatencyMs(),
                        interval.medianLatencyMs(),
                        interval.latencyAtPercentileMs(95.0),
                        interval.latencyAtPercentileMs(99.0),
                        interval.latencyAtPercentileMs(99.9),
                        interval.maxLatencyMs(),
                        total.runTimeMs() / 1000f,
                        opRateUncertainty.getUncertainty(),
                        interval.errorCount));
    }

    public void summarise() {
        output.println("\n");
        output.println("Results:");

        TimingIntervals opHistory = new TimingIntervals(opTypeToSummaryTimingInterval);
        TimingInterval history = this.totalSummaryInterval;
        output.println(
                String.format("Op rate                   : %,8.0f op/s  %s", history.opRate(), opHistory.opRates()));
        output.println(String.format(
                "Partition rate            : %,8.0f pk/s  %s", history.partitionRate(), opHistory.partitionRates()));
        output.println(
                String.format("Row rate                  : %,8.0f row/s %s", history.rowRate(), opHistory.rowRates()));
        output.println(String.format(
                "Latency mean              : %6.1f ms %s", history.meanLatencyMs(), opHistory.meanLatencies()));
        output.println(String.format(
                "Latency median            : %6.1f ms %s", history.medianLatencyMs(), opHistory.medianLatencies()));
        output.println(String.format(
                "Latency 95th percentile   : %6.1f ms %s",
                history.latencyAtPercentileMs(95.0), opHistory.latenciesAtPercentile(95.0)));
        output.println(String.format(
                "Latency 99th percentile   : %6.1f ms %s",
                history.latencyAtPercentileMs(99.0), opHistory.latenciesAtPercentile(99.0)));
        output.println(String.format(
                "Latency 99.9th percentile : %6.1f ms %s",
                history.latencyAtPercentileMs(99.9), opHistory.latenciesAtPercentile(99.9)));
        output.println(String.format(
                "Latency max               : %6.1f ms %s", history.maxLatencyMs(), opHistory.maxLatencies()));
        output.println(String.format(
                "Total partitions          : %,10d %s", history.partitionCount, opHistory.partitionCounts()));
        output.println(
                String.format("Total errors              : %,10d %s", history.errorCount, opHistory.errorCounts()));
        output.println("Total operation time      : " + formatDuration(history.runTimeMs()));
        output.println("");
    }

    public static void summarise(List<String> ids, List<StressMetrics> summarise, ResultLogger out) {
        int idLen = 0;
        for (String id : ids) idLen = Math.max(id.length(), idLen);
        String formatstr = "%" + idLen + "s, ";
        printHeader(String.format(formatstr, "id"), out);
        for (int i = 0; i < ids.size(); i++) {
            for (Map.Entry<String, TimingInterval> type :
                    summarise.get(i).opTypeToSummaryTimingInterval.entrySet()) {
                printRow(
                        String.format(formatstr, ids.get(i)),
                        type.getKey(),
                        type.getValue(),
                        type.getValue(),
                        summarise.get(i).rowRateUncertainty,
                        out);
            }
            TimingInterval hist = summarise.get(i).totalSummaryInterval;
            printRow(
                    String.format(formatstr, ids.get(i)),
                    "total",
                    hist,
                    hist,
                    summarise.get(i).rowRateUncertainty,
                    out);
        }
    }

    public boolean wasCancelled() {
        return cancelled;
    }

    public void add(Consumer consumer) {
        consumers.add(consumer);
    }

    public double opRate() {
        return totalSummaryInterval.opRate();
    }

    static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        return String.format("%02d:%02d:%02d", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }
}
