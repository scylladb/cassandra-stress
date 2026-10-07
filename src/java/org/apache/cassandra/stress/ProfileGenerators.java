// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.cassandra.stress.core.CqlTypes;
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.TableSchema;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.values.BigDecimals;
import org.apache.cassandra.stress.generate.values.BigIntegers;
import org.apache.cassandra.stress.generate.values.Booleans;
import org.apache.cassandra.stress.generate.values.Bytes;
import org.apache.cassandra.stress.generate.values.Dates;
import org.apache.cassandra.stress.generate.values.Doubles;
import org.apache.cassandra.stress.generate.values.Floats;
import org.apache.cassandra.stress.generate.values.Generator;
import org.apache.cassandra.stress.generate.values.GeneratorConfig;
import org.apache.cassandra.stress.generate.values.Inets;
import org.apache.cassandra.stress.generate.values.Integers;
import org.apache.cassandra.stress.generate.values.Lists;
import org.apache.cassandra.stress.generate.values.LocalDates;
import org.apache.cassandra.stress.generate.values.Longs;
import org.apache.cassandra.stress.generate.values.Sets;
import org.apache.cassandra.stress.generate.values.SmallInts;
import org.apache.cassandra.stress.generate.values.Strings;
import org.apache.cassandra.stress.generate.values.TimeUUIDs;
import org.apache.cassandra.stress.generate.values.Times;
import org.apache.cassandra.stress.generate.values.TinyInts;
import org.apache.cassandra.stress.generate.values.UUIDs;
import org.apache.cassandra.stress.settings.StressSettings;

final class ProfileGenerators {
    private final List<ColumnInfo> partitionKeys = new ArrayList<>();
    private final List<ColumnInfo> clusteringColumns = new ArrayList<>();
    private final List<ColumnInfo> valueColumns = new ArrayList<>();
    private final boolean[] descendingClustering;

    ProfileGenerators(TableSchema table, Map<String, GeneratorConfig> columnConfigs, boolean skipUnsupportedColumns) {
        List<ColumnInfo> unsupportedKeys = new ArrayList<>();
        List<ColumnInfo> unsupportedValues = new ArrayList<>();

        add(table.partitionKey(), partitionKeys, columnConfigs, unsupportedKeys);
        add(table.clusteringColumns(), clusteringColumns, columnConfigs, unsupportedKeys);
        descendingClustering = new boolean[table.clusteringColumns().size()];
        int depth = 0;
        for (ColumnSchema column : table.clusteringColumns()) descendingClustering[depth++] = column.descending();
        add(table.valueColumns(), valueColumns, columnConfigs, unsupportedValues);

        String tableName = table.name();
        String level = skipUnsupportedColumns ? "WARNING" : "ERROR";
        for (ColumnInfo column : unsupportedValues)
            System.err.printf(
                    Locale.ROOT, "%s: Table '%s' has column '%s' of unsupported type%n", level, tableName, column.name);
        for (ColumnInfo column : unsupportedKeys)
            System.err.printf(
                    Locale.ROOT, "ERROR: Table '%s' has column '%s' of unsupported type%n", tableName, column.name);
        if (!unsupportedKeys.isEmpty())
            throw new IllegalArgumentException("Table '" + tableName + "' has key columns of unsupported types");
    }

    private static void add(
            Collection<ColumnSchema> columns,
            List<ColumnInfo> target,
            Map<String, GeneratorConfig> columnConfigs,
            List<ColumnInfo> unsupported) {
        for (ColumnSchema metadata : columns) {
            String name = metadata.name();
            ColumnInfo column = new ColumnInfo(
                    name,
                    CqlTypes.name(metadata.type()).toLowerCase(Locale.ROOT),
                    CqlTypes.elementName(metadata.type()).toLowerCase(Locale.ROOT),
                    columnConfigs.get(name));
            if (CqlTypes.isSupported(metadata.type())) target.add(column);
            else unsupported.add(column);
        }
    }

    PartitionGenerator newGenerator(StressSettings settings) {
        return new PartitionGenerator(
                generators(partitionKeys),
                generators(clusteringColumns),
                generators(valueColumns),
                settings.generate.order,
                descendingClustering.clone());
    }

    private static List<Generator> generators(List<ColumnInfo> columns) {
        List<Generator> result = new ArrayList<>(columns.size());
        for (ColumnInfo column : columns) result.add(column.generator());
        return result;
    }

    record ColumnInfo(String name, String type, String collectionType, GeneratorConfig config) {
        Generator generator() {
            return generator(name, type, collectionType, config);
        }

        static Generator generator(String name, String type, String collectionType, GeneratorConfig config) {
            return switch (type.toUpperCase(Locale.ROOT)) {
                case "ASCII", "TEXT", "VARCHAR" -> new Strings(name, config);
                case "BIGINT", "COUNTER" -> new Longs(name, config);
                case "BLOB" -> new Bytes(name, config);
                case "BOOLEAN" -> new Booleans(name, config);
                case "DECIMAL" -> new BigDecimals(name, config);
                case "DOUBLE" -> new Doubles(name, config);
                case "FLOAT" -> new Floats(name, config);
                case "INET" -> new Inets(name, config);
                case "INT" -> new Integers(name, config);
                case "VARINT" -> new BigIntegers(name, config);
                case "TIMESTAMP" -> new Dates(name, config);
                case "UUID" -> new UUIDs(name, config);
                case "TIMEUUID" -> new TimeUUIDs(name, config);
                case "TINYINT" -> new TinyInts(name, config);
                case "SMALLINT" -> new SmallInts(name, config);
                case "TIME" -> new Times(name, config);
                case "DATE" -> new LocalDates(name, config);
                case "SET" -> new Sets(name, generator(name, collectionType, null, config), config);
                case "LIST" -> new Lists(name, generator(name, collectionType, null, config), config);
                default ->
                    throw new UnsupportedOperationException("Because of this name: " + name
                            + " if you removed it from the yaml and are still seeing this, make sure to drop table");
            };
        }
    }
}
