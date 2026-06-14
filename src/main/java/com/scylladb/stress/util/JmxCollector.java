package com.scylladb.stress.util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

public class JmxCollector implements Callable<JmxCollector.GcStats> {

    public static class GcStats {
        public final double count;
        public final double bytes;
        public final double maxms;
        public final double summs;
        public final double sumsqms;
        public final double sdvms;

        public GcStats(double count, double bytes, double maxms, double summs, double sumsqms) {
            this.count = count;
            this.bytes = bytes;
            this.maxms = maxms;
            this.summs = summs;
            this.sumsqms = sumsqms;
            double mean = count > 0 ? summs / count : 0;
            double stdev = count > 0 ? Math.sqrt((sumsqms / count) - (mean * mean)) : 0;
            if (Double.isNaN(stdev))
                stdev = 0;
            this.sdvms = stdev;
        }

        public GcStats(double fill) {
            this(fill, fill, fill, fill, fill);
        }

        public static GcStats aggregate(List<GcStats> stats) {
            double count = 0, bytes = 0, maxms = 0, summs = 0, sumsqms = 0;
            for (GcStats stat : stats) {
                count += stat.count;
                bytes += stat.bytes;
                maxms += stat.maxms;
                summs += stat.summs;
                sumsqms += stat.sumsqms;
            }
            return new GcStats(count, bytes, maxms, summs, sumsqms);
        }
    }

    public JmxCollector(Collection<String> hosts, int port) {
        throw new UnsupportedOperationException("JMX collection via NodeProbe is not available in this build");
    }

    @Override
    public GcStats call() {
        return new GcStats(0);
    }
}
