// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.report;

import org.apache.cassandra.stress.StressAction.MeasurementSink;

public final class Timer {
    private final String opType;
    private final MeasurementSink sink;

    private long intendedTimeNs;
    private long startTimeNs;

    public Timer(String opType, MeasurementSink sink) {
        this.opType = opType;
        this.sink = sink;
    }

    public void stop(long partitionCount, long rowCount, boolean error) {
        sink.record(opType, intendedTimeNs, startTimeNs, System.nanoTime(), rowCount, partitionCount, error);
        resetTimes();
    }

    private void resetTimes() {
        intendedTimeNs = 0;
        startTimeNs = 0;
    }

    public void intendedTimeNs(long v) {
        intendedTimeNs = v;
    }

    public void start() {
        startTimeNs = System.nanoTime();
    }
}
