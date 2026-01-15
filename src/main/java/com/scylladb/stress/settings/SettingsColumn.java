package com.scylladb.stress.settings;
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.*;

import com.scylladb.serializers.AsciiSerializer;
import com.scylladb.serializers.TimeUUIDSerializer;
import com.scylladb.serializers.UTF8Serializer;
import com.scylladb.stress.generate.Distribution;
import com.scylladb.stress.generate.DistributionFactory;
import com.scylladb.stress.generate.DistributionFixed;
import com.scylladb.stress.generate.values.ComparisonType;
import com.scylladb.stress.generate.values.Type;
import com.scylladb.stress.util.ResultLogger;
import com.scylladb.utils.ByteBufferUtil;
import com.scylladb.utils.FastByteOperations;

/**
 * For parsing column options
 */
public class SettingsColumn implements Serializable
{
    private static final String[] SUPPORTED_COMPARATORS = {"", "AsciiType", "UTF8Type"};


    public final int maxColumnsPerKey;
    public transient List<ByteBuffer> names;
    public final List<String> namestrs;
    public final String comparator;
    public final String timestamp;
    public final boolean variableColumnCount;
    public final boolean slice;
    public final DistributionFactory sizeDistribution;
    public final DistributionFactory countDistribution;

    public SettingsColumn(GroupedOptions options)
    {
        this((Options) options,
                options instanceof NameOptions ? (NameOptions) options : null,
                options instanceof CountOptions ? (CountOptions) options : null
        );
    }

    private static Type<?> parseType(String type) {
        switch (type) {
            case "AsciiType":
                return new Type<>(AsciiSerializer.instance, ComparisonType.BYTE_ORDER, null);
            case "UTF8Type":
                return new Type<>(UTF8Serializer.instance, ComparisonType.BYTE_ORDER, null);
            case "TimeUUIDType":
                return new Type<>(TimeUUIDSerializer.instance, ComparisonType.CUSTOM, (o1, o2) -> {
                    long t1 = o1.getLong(o1.position());
                    long t2 = o2.getLong(o2.position());
                    return Long.compare(t1, t2);
                });
            default:
                throw new IllegalArgumentException(type + " is not a supported type");
        }
    }

    public SettingsColumn(Options options, NameOptions name, CountOptions count)
    {
        sizeDistribution = options.size.get();
        {
            timestamp = options.timestamp.value();
            comparator = options.comparator.value();

            try
            {
                parseType(comparator);
            }
            catch (Exception e)
            {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }
        if (name != null)
        {
            assert count == null;

            Type<?> type = parseType(this.comparator);

            final String[] names = name.name.value().split(",");
            this.names = new ArrayList<>(names.length);

            for (String columnName : names) {
                if (type.getSerializer().getType() == String.class) {
                    Type<String> t = (Type<String>) type;
                    this.names.add(t.getSerializer().serialize(columnName));
                } else if (type.getSerializer().getType() == UUID.class) {
                    Type<UUID> t = (Type<UUID>) type;
                    this.names.add(t.getSerializer().serialize(UUID.fromString(columnName)));
                } else {
                    throw new UnsupportedOperationException();
                }
            }
            Collections.sort(this.names, FastByteOperations::compareUnsigned);
            this.namestrs = new ArrayList<>();
            for (ByteBuffer columnName : this.names) {
                if (type.getSerializer().getType() == String.class) {
                    Type<String> t = (Type<String>) type;
                    this.namestrs.add(t.getSerializer().toString(t.compose(columnName)));
                } else if (type.getSerializer().getType() == UUID.class) {
                    Type<UUID> t = (Type<UUID>) type;
                    this.namestrs.add(t.getSerializer().toString(t.compose(columnName)));
                }
            }

            final int nameCount = this.names.size();
            countDistribution = new DistributionFactory()
            {
                @Override
                public Distribution get()
                {
                    return new DistributionFixed(nameCount);
                }
                @Override
                public String getConfigAsString(){return String.format("Count:  fixed=%d", nameCount);}
            };
        }
        else
        {
            this.countDistribution = count.count.get();
            ByteBuffer[] names = new ByteBuffer[(int) countDistribution.get().maxValue()];
            String[] namestrs = new String[(int) countDistribution.get().maxValue()];
            for (int i = 0 ; i < names.length ; i++)
                names[i] = ByteBufferUtil.bytes("C" + i);
            Arrays.sort(names, FastByteOperations::compareUnsigned);
            try
            {
                for (int i = 0 ; i < names.length ; i++)
                    namestrs[i] = ByteBufferUtil.string(names[i]);
            }
            catch (CharacterCodingException e)
            {
                throw new RuntimeException(e);
            }
            this.names = Arrays.asList(names);
            this.namestrs = Arrays.asList(namestrs);
        }
        maxColumnsPerKey = (int) countDistribution.get().maxValue();
        variableColumnCount = countDistribution.get().minValue() < maxColumnsPerKey;
        slice = options.slice.setByUser();
    }

    // Option Declarations

    private static abstract class Options extends GroupedOptions
    {
        final OptionSimple superColumns = new OptionSimple("super=", "[0-9]+", "0", "Number of super columns to use (no super columns used if not specified)", false);
        final OptionSimple comparator = new OptionSimple("comparator=", "TimeUUIDType|AsciiType|UTF8Type", "AsciiType", "Column Comparator to use", false);
        final OptionSimple slice = new OptionSimple("slice", "", null, "If set, range slices will be used for reads, otherwise a names query will be", false);
        final OptionSimple timestamp = new OptionSimple("timestamp=", "[0-9]+", null, "If set, all columns will be written with the given timestamp", false);
        final OptionDistribution size = new OptionDistribution("size=", "FIXED(34)", "Cell size distribution");
    }

    private static final class NameOptions extends Options
    {
        final OptionSimple name = new OptionSimple("names=", ".*", null, "Column names", true);

        @Override
        public List<? extends Option> options()
        {
            return Arrays.asList(name, slice, superColumns, comparator, timestamp, size);
        }
    }

    private static final class CountOptions extends Options
    {
        final OptionDistribution count = new OptionDistribution("n=", "FIXED(5)", "Cell count distribution, per operation");

        @Override
        public List<? extends Option> options()
        {
            return Arrays.asList(count, slice, superColumns, comparator, timestamp, size);
        }
    }

    // CLI Utility Methods
    public void printSettings(ResultLogger out)
    {
        out.printf("  Max Columns Per Key: %d%n",maxColumnsPerKey);
        out.printf("  Column Names: %s%n",namestrs);
        out.printf("  Comparator: %s%n", comparator);
        out.printf("  Timestamp: %s%n", timestamp);
        out.printf("  Variable Column Count: %b%n", variableColumnCount);
        out.printf("  Slice: %b%n", slice);
        if (sizeDistribution != null){
            out.println("  Size Distribution: " + sizeDistribution.getConfigAsString());
        };
        if (sizeDistribution != null){
            out.println("  Count Distribution: " + countDistribution.getConfigAsString());
        };
    }


    static SettingsColumn get(Map<String, String[]> clArgs)
    {
        String[] params = clArgs.remove("-col");
        if (params == null)
            return new SettingsColumn(new CountOptions());

        GroupedOptions options = GroupedOptions.select(params, new NameOptions(), new CountOptions());
        if (options == null)
        {
            printHelp();
            System.out.println("Invalid -col options provided, see output for valid options");
            System.exit(1);
        }
        return new SettingsColumn(options);
    }

    static void printHelp()
    {
        GroupedOptions.printOptions(System.out, "-col", new NameOptions(), new CountOptions());
    }

    static Runnable helpPrinter()
    {
        return SettingsColumn::printHelp;
    }

}
