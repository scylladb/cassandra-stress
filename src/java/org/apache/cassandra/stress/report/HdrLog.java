// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.report;

import java.io.File;
import java.io.FileNotFoundException;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;

public final class HdrLog implements AutoCloseable {
    private final HistogramLogWriter writer;
    private final Object lock = new Object();

    private HdrLog(HistogramLogWriter writer, long epochMs) {
        this.writer = writer;
        final long roundedEpoch = epochMs - (epochMs % 1000);
        writer.outputComment("Logging op latencies for Cassandra Stress");
        writer.outputLogFormatVersion();
        writer.outputBaseTime(roundedEpoch);
        writer.setBaseTime(roundedEpoch);
        writer.outputStartTime(roundedEpoch);
        writer.outputLegend();
    }

    public static HdrLog open(File file) {
        if (file == null) {
            return null;
        }
        try {
            return new HdrLog(new HistogramLogWriter(file), System.currentTimeMillis());
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void write(Histogram histogram) {
        synchronized (lock) {
            writer.outputIntervalHistogram(histogram);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            writer.close();
        }
    }
}
