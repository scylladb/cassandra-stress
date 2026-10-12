package org.apache.cassandra.stress.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TimingIntervalTest {
    private static Locale previous;

    @BeforeAll
    static void useAGermanLocale() {
        previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
    }

    @AfterAll
    static void restoreLocale() {
        Locale.setDefault(previous);
    }

    private static TimingInterval interval(boolean fixed, long startNs, long endNs, long... serviceNanos) {
        TimingInterval interval = new TimingInterval(fixed);
        interval.startNanos(startNs);
        interval.endNanos(endNs);
        for (long nanos : serviceNanos) {
            interval.serviceTime().recordValue(nanos);
        }
        return interval;
    }

    @Test
    void computesRatesOverTheInterval() {
        TimingInterval interval = interval(false, 0, 2_000_000_000L, 1_000_000, 3_000_000);
        interval.partitionCount = 10;
        interval.rowCount = 40;
        assertEquals(1.0, interval.opRate(), 1e-9);
        assertEquals(5.0, interval.partitionRate(), 1e-9);
        assertEquals(20.0, interval.rowRate(), 1e-9);
        assertEquals(2_000, interval.runTimeMs());
        assertEquals(2, interval.operationCount());
    }

    @Test
    void reportsLatenciesInMilliseconds() {
        TimingInterval interval = interval(false, 0, 1_000_000_000L, 1_000_000, 2_000_000, 3_000_000);
        assertEquals(2.0, interval.meanLatencyMs(), 0.01);
        assertEquals(3.0, interval.maxLatencyMs(), 0.01);
        assertEquals(2.0, interval.medianLatencyMs(), 0.01);
        assertEquals(3.0, interval.latencyAtPercentileMs(99.0), 0.01);
    }

    @Test
    void fixedRateUsesResponseTimeWhenRecorded() {
        TimingInterval interval = interval(true, 0, 1_000_000_000L, 1_000_000);
        assertEquals(1.0, interval.maxLatencyMs(), 0.01);
        interval.responseTime().recordValue(9_000_000);
        assertEquals(9.0, interval.maxLatencyMs(), 0.01);
    }

    @Test
    void addMergesBoundsCountsAndHistograms() {
        TimingInterval total = new TimingInterval(false);
        TimingInterval first = interval(false, 100, 200, 1_000);
        first.errorCount = 1;
        first.rowCount = 2;
        first.partitionCount = 3;
        TimingInterval second = interval(false, 50, 150, 2_000);
        second.waitTime().recordValue(5);
        second.responseTime().recordValue(7);

        total.add(first);
        total.add(second);

        assertEquals(50, total.startNanos());
        assertEquals(200, total.endNanos());
        assertEquals(1, total.errorCount);
        assertEquals(2, total.rowCount);
        assertEquals(3, total.partitionCount);
        assertEquals(2, total.serviceTime().getTotalCount());
        assertEquals(1, total.waitTime().getTotalCount());
        assertEquals(1, total.responseTime().getTotalCount());
    }

    @Test
    void resetClearsEverything() {
        TimingInterval interval = interval(false, 1, 2, 1_000);
        interval.responseTime().recordValue(1);
        interval.waitTime().recordValue(1);
        interval.errorCount = 4;
        interval.reset();
        assertEquals(Long.MAX_VALUE, interval.startNanos());
        assertEquals(Long.MIN_VALUE, interval.endNanos());
        assertEquals(0, interval.errorCount);
        assertEquals(0, interval.serviceTime().getTotalCount());
        assertEquals(0, interval.responseTime().getTotalCount());
        assertEquals(0, interval.waitTime().getTotalCount());
    }

    @Test
    void formatsEachParameter() {
        TimingInterval interval = interval(false, 0, 1_000_000_000L, 1_500_000);
        interval.errorCount = 1234;
        interval.partitionCount = 5;
        interval.rowCount = 7;
        assertEquals("1", interval.getStringValue(TimingInterval.TimingParameter.OPRATE));
        assertEquals("7", interval.getStringValue(TimingInterval.TimingParameter.ROWRATE));
        assertEquals("5", interval.getStringValue(TimingInterval.TimingParameter.PARTITIONRATE));
        assertEquals("1.5", interval.getStringValue(TimingInterval.TimingParameter.MEANLATENCY));
        assertEquals("1.5", interval.getStringValue(TimingInterval.TimingParameter.MAXLATENCY));
        assertEquals("1.5", interval.getStringValue(TimingInterval.TimingParameter.MEDIANLATENCY));
        assertEquals("1.5", interval.getStringValue(TimingInterval.TimingParameter.RANKLATENCY, 50));
        assertEquals("1,234", interval.getStringValue(TimingInterval.TimingParameter.ERRORCOUNT));
        assertEquals("5", interval.getStringValue(TimingInterval.TimingParameter.PARTITIONCOUNT));
        assertEquals("7", interval.getStringValue(TimingInterval.TimingParameter.ADJROWRATE));
    }

    @Test
    void intervalsJoinEachOperationType() {
        Map<String, TimingInterval> map = new LinkedHashMap<>();
        map.put("READ", interval(false, 0, 1_000_000_000L, 1_000_000, 1_000_000));
        map.put("WRITE", interval(false, 500_000_000L, 2_000_000_000L, 2_000_000));
        map.get("WRITE").errorCount = 3;
        TimingIntervals intervals = new TimingIntervals(map);

        assertEquals("[READ: 2 op/s, WRITE: 1 op/s]", intervals.opRates());
        assertEquals("[READ: 0, WRITE: 3]", intervals.errorCounts());
        assertEquals("[READ: 1.0 ms, WRITE: 2.0 ms]", intervals.meanLatencies());
        assertEquals("[READ: 1.0 ms, WRITE: 2.0 ms]", intervals.latenciesAtPercentile(99));
        assertEquals("[READ: 0, WRITE: 0]", intervals.partitionCounts());
        assertEquals("[READ: 0 pk/s, WRITE: 0 pk/s]", intervals.partitionRates());
        assertEquals("[READ: 0 row/s, WRITE: 0 row/s]", intervals.rowRates());
        assertEquals("[READ: 1.0 ms, WRITE: 2.0 ms]", intervals.maxLatencies());
        assertEquals("[READ: 1.0 ms, WRITE: 2.0 ms]", intervals.medianLatencies());
        assertEquals("[]", new TimingIntervals(Map.of()).opRates());
    }
}
