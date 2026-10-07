// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.stress.generate.DistributionFixed;
import org.apache.cassandra.stress.util.ResultLogger;

public class SettingsColumn {

    public final int maxColumnsPerKey;
    public List<ByteBuffer> names;
    public final List<String> namestrs;
    public final String timestamp;
    public final boolean variableColumnCount;
    public final boolean slice;
    public final DistributionFactory sizeDistribution;
    public final DistributionFactory countDistribution;

    public SettingsColumn(GroupedOptions options) {
        this(
                (Options) options,
                options instanceof NameOptions nameOptions ? nameOptions : null,
                options instanceof CountOptions countOptions ? countOptions : null);
    }

    public SettingsColumn(Options options, NameOptions name, CountOptions count) {
        sizeDistribution = options.size.get();
        timestamp = options.timestamp.value();
        if (name != null) {
            assert count == null;

            List<ByteBuffer> sortedNames = new ArrayList<>();
            for (String columnName : name.name.value().split(",")) {
                sortedNames.add(ByteBuffer.wrap(columnName.getBytes(StandardCharsets.UTF_8)));
            }
            this.names = sortedByUnsignedBytes(sortedNames);
            this.namestrs = decode(this.names);

            final int nameCount = this.names.size();
            countDistribution = new DistributionFactory() {
                @Override
                public Distribution get() {
                    return new DistributionFixed(nameCount);
                }

                @Override
                public String getConfigAsString() {
                    return String.format(Locale.ROOT, "Count:  fixed=%d", nameCount);
                }
            };
        } else {
            this.countDistribution = count.count.get();
            List<ByteBuffer> generatedNames = new ArrayList<>();
            for (int i = 0; i < (int) countDistribution.get().maxValue(); i++) {
                generatedNames.add(ByteBuffer.wrap(("C" + i).getBytes(StandardCharsets.UTF_8)));
            }
            this.names = sortedByUnsignedBytes(generatedNames);
            this.namestrs = decode(this.names);
        }
        maxColumnsPerKey = (int) countDistribution.get().maxValue();
        variableColumnCount = countDistribution.get().minValue() < maxColumnsPerKey;
        slice = options.slice.setByUser();
    }

    private static List<ByteBuffer> sortedByUnsignedBytes(List<ByteBuffer> names) {
        names.sort((left, right) -> Arrays.compareUnsigned(bytesOf(left), bytesOf(right)));
        return names;
    }

    private static byte[] bytesOf(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }

    private static List<String> decode(List<ByteBuffer> names) {
        List<String> decoded = new ArrayList<>(names.size());
        for (ByteBuffer columnName : names) {
            decoded.add(new String(bytesOf(columnName), StandardCharsets.UTF_8));
        }
        return decoded;
    }

    private abstract static class Options extends GroupedOptions {
        final OptionSimple slice = new OptionSimple(
                "slice",
                "",
                null,
                "If set, range slices will be used for reads, otherwise a names query will be",
                false);
        final OptionSimple timestamp = new OptionSimple(
                "timestamp=", "[0-9]+", null, "If set, all columns will be written with the given timestamp", false);
        final OptionDistribution size = new OptionDistribution("size=", "FIXED(34)", "Cell size distribution");
    }

    private static final class NameOptions extends Options {
        final OptionSimple name = new OptionSimple("names=", ".*", null, "Column names", true);

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(name, slice, timestamp, size);
        }
    }

    private static final class CountOptions extends Options {
        final OptionDistribution count =
                new OptionDistribution("n=", "FIXED(5)", "Cell count distribution, per operation");

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(count, slice, timestamp, size);
        }
    }

    public void printSettings(ResultLogger out) {
        out.printf("  Max Columns Per Key: %d%n", maxColumnsPerKey);
        out.printf("  Column Names: %s%n", namestrs);
        out.printf("  Timestamp: %s%n", timestamp);
        out.printf("  Variable Column Count: %b%n", variableColumnCount);
        out.printf("  Slice: %b%n", slice);
        if (sizeDistribution != null) {
            out.println("  Size Distribution: " + sizeDistribution.getConfigAsString());
        }
        if (countDistribution != null) {
            out.println("  Count Distribution: " + countDistribution.getConfigAsString());
        }
    }

    static SettingsColumn get(Map<String, String[]> clArgs) {
        String[] params = clArgs.remove("-col");
        if (params == null) {
            return new SettingsColumn(new CountOptions());
        }

        GroupedOptions options = GroupedOptions.select(params, new NameOptions(), new CountOptions());
        if (options == null) {
            throw new InvalidSettingsException(
                    "Invalid -col options provided, see output for valid options", SettingsColumn::printHelp);
        }
        return new SettingsColumn(options);
    }

    static void printHelp() {
        GroupedOptions.printOptions(System.out, "-col", new NameOptions(), new CountOptions());
    }

    static Runnable helpPrinter() {
        return () -> printHelp();
    }
}
