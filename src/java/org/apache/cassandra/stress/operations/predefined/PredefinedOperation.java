// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations.predefined;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.cassandra.stress.Operation;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.stress.generate.DistributionFixed;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.RatioDistribution;
import org.apache.cassandra.stress.generate.Row;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.operations.PartitionOperation;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.Command;
import org.apache.cassandra.stress.settings.CqlVersion;
import org.apache.cassandra.stress.settings.StressSettings;

public abstract class PredefinedOperation extends PartitionOperation {
    public static final byte[] EMPTY_BYTE_ARRAY = {};
    public final Command type;
    private final Distribution columnCount;
    private Object cqlCache;

    public PredefinedOperation(
            Command type, Timer timer, PartitionGenerator generator, SeedManager seedManager, StressSettings settings) {
        super(timer, settings, spec(generator, seedManager, settings.insert.rowPopulationRatio.get()));
        this.type = type;
        this.columnCount = settings.columns.countDistribution.get();
    }

    private static DataSpec spec(
            PartitionGenerator generator, SeedManager seedManager, RatioDistribution rowPopulationCount) {
        return new DataSpec(generator, seedManager, new DistributionFixed(1), rowPopulationCount, 1);
    }

    public boolean isCql3() {
        return settings.mode.cqlVersion == CqlVersion.CQL3;
    }

    public Object getCqlCache() {
        return cqlCache;
    }

    public void storeCqlCache(Object val) {
        cqlCache = val;
    }

    protected ByteBuffer getKey() {
        return (ByteBuffer) partitions.getFirst().getPartitionKey(0);
    }

    @SuppressWarnings("ArrayRecordComponent")
    record ColumnSelection(int[] indices, int lb, int ub) {
        <V> List<V> select(List<V> in) {
            if (indices() == null) {
                return new ArrayList<>(in.subList(lb(), ub()));
            }
            List<V> out = new ArrayList<>(indices().length);
            for (int i : indices()) {
                out.add(in.get(i));
            }
            return out;
        }

        int count() {
            return indices() != null ? indices().length : ub() - lb();
        }
    }

    @Override
    public String toString() {
        return type.toString();
    }

    ColumnSelection select() {
        if (settings.columns.slice) {
            int count = (int) columnCount.next();
            int start;
            if (count == settings.columns.maxColumnsPerKey) {
                start = 0;
            } else {
                start = 1 + ThreadLocalRandom.current().nextInt(settings.columns.maxColumnsPerKey - count);
            }
            return new ColumnSelection(null, start, start + count);
        }

        int count = (int) columnCount.next();
        int totalCount = settings.columns.names.size();
        if (count == settings.columns.names.size()) {
            return new ColumnSelection(null, 0, count);
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int[] indices = new int[count];
        int c = 0;
        int o = 0;
        while (c < count && count + o < totalCount) {
            int leeway = totalCount - (count + o);
            int spreadover = count - c;
            o = (int) (o + Math.round(rnd.nextDouble() * (leeway / (double) spreadover)));
            indices[c] = o + c;
            c++;
        }
        while (c < count) {
            indices[c] = o + c;
            c++;
        }
        return new ColumnSelection(indices, 0, 0);
    }

    protected List<ByteBuffer> getColumnValues() {
        return getColumnValues(new ColumnSelection(null, 0, settings.columns.names.size()));
    }

    protected List<ByteBuffer> getColumnValues(ColumnSelection columns) {
        Row row = partitions.getFirst().next();
        ByteBuffer[] values = new ByteBuffer[columns.count()];
        int c = 0;
        if (columns.indices() != null) {
            for (int i : columns.indices()) {
                values[c++] = (ByteBuffer) row.get(i);
            }
        } else {
            for (int i = columns.lb(); i < columns.ub(); i++) {
                values[c++] = (ByteBuffer) row.get(i);
            }
        }
        return Arrays.asList(values);
    }

    public static Operation operation(
            Command type,
            Timer timer,
            PartitionGenerator generator,
            SeedManager seedManager,
            StressSettings settings,
            DistributionFactory counteradd) {
        return switch (type) {
            case READ -> new CqlReader(timer, generator, seedManager, settings);
            case COUNTER_READ -> new CqlCounterGetter(timer, generator, seedManager, settings);
            case WRITE -> new CqlInserter(timer, generator, seedManager, settings);
            case COUNTER_WRITE -> new CqlCounterAdder(counteradd, timer, generator, seedManager, settings);
            default -> throw new UnsupportedOperationException();
        };
    }
}
