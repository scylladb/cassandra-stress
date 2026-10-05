package org.apache.cassandra.stress.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StressMetricsTest {
    @Test
    void headerEndsAtErrors() {
        assertEquals(
                List.of(
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
                        "errors"),
                StressMetrics.HEADMETRICS);
    }

    @Test
    void headerHasNoGcField() {
        assertFalse(StressMetrics.HEAD.contains("gc"));
    }

    @ParameterizedTest
    @CsvSource({"0, 00:00:00", "999, 00:00:00", "3723000, 01:02:03", "90000000, 25:00:00"})
    void formatsTheTotalTimeAsHoursMinutesSeconds(long millis, String expected) {
        assertEquals(expected, StressMetrics.formatDuration(millis));
    }
}
