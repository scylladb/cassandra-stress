package org.apache.cassandra.stress.report;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

public class StressMetricsTest
{
    @Test
    public void headerEndsAtErrors()
    {
        assertArrayEquals(new String[]{ "type", "total ops", "op/s", "pk/s", "row/s", "mean", "med", ".95", ".99", ".999", "max", "time", "stderr", "errors" },
                          StressMetrics.HEADMETRICS);
    }

    @Test
    public void headerHasNoGcField()
    {
        assertFalse(StressMetrics.HEAD.contains("gc"));
    }
}
