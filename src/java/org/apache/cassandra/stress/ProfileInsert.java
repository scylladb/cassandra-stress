// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.apache.cassandra.stress.core.CqlTypes;
import org.apache.cassandra.stress.driver.BatchType;
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.TableSchema;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.RatioDistributionFactory;
import org.apache.cassandra.stress.settings.OptionDistribution;
import org.apache.cassandra.stress.settings.OptionRatioDistribution;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;

record ProfileInsert(
        String cql,
        DistributionFactory partitions,
        RatioDistributionFactory selectChance,
        RatioDistributionFactory rowPopulation,
        ConsistencyLevel consistencyLevel,
        ConsistencyLevel serialConsistencyLevel,
        BatchType batchType) {
    private static final Pattern LOWERCASE_ALPHANUMERIC = Pattern.compile("[a-z0-9_]+");
    static final int MAX_LOGGED_BATCH_ROWS = 65535;

    static ProfileInsert of(
            TableSchema table,
            String tableName,
            Map<String, String> options,
            PartitionGenerator generator,
            StressSettings settings) {
        Map<String, String> insert = options == null ? new HashMap<>() : new HashMap<>(options);
        StressProfile.lowerCase(insert);

        DistributionFactory partitions =
                select(settings.insert.batchsize, "partitions", "fixed(1)", insert, OptionDistribution.BUILDER);
        RatioDistributionFactory selectChance =
                select(settings.insert.selectRatio, "select", "fixed(1)/1", insert, OptionRatioDistribution.BUILDER);
        RatioDistributionFactory rowPopulation = select(
                settings.insert.rowPopulationRatio,
                "row-population",
                "fixed(1)/1",
                insert,
                OptionRatioDistribution.BUILDER);
        ConsistencyLevel consistencyLevel = selectConsistency(
                settings.insert.consistencyLevel, "consistencylevel", settings.command.consistencyLevel, insert);
        ConsistencyLevel serialConsistencyLevel = selectConsistency(
                settings.insert.serialConsistencyLevel,
                "serialconsistencylevel",
                settings.command.serialConsistencyLevel,
                insert);
        String batchType = insert.remove("batchtype");
        BatchType type = settings.insert.batchType != null
                ? settings.insert.batchType
                : batchType == null ? BatchType.LOGGED : BatchType.valueOf(batchType);
        if (!insert.isEmpty()) throw new IllegalArgumentException("Unrecognised insert option(s): " + insert);

        ProfileInsert spec = new ProfileInsert(
                cql(table, tableName),
                partitions,
                selectChance,
                rowPopulation,
                consistencyLevel,
                serialConsistencyLevel,
                type);
        spec.checkBatchSize(generator, settings.insert.visits.get());
        return spec;
    }

    private void checkBatchSize(PartitionGenerator generator, Distribution visits) {
        double maxRows = selectChance.get().max() * partitions.get().maxValue() * generator.maxRowCount;
        double maxBatchSize = maxRows * (1d / visits.minValue());

        if (generator.maxRowCount > 100 * 1000 * 1000)
            System.err.printf(
                    "WARNING: You have defined a schema that permits very large partitions (%.0f max rows (>100M))%n",
                    generator.maxRowCount);
        if (batchType == BatchType.LOGGED && maxBatchSize > MAX_LOGGED_BATCH_ROWS)
            throw new IllegalArgumentException(String.format(
                    "You have defined a workload that generates batches with more than 65k rows (%.0f), but have"
                            + " required the use of LOGGED batches. There is a 65k row limit on a single batch.",
                    maxRows));
        if (maxBatchSize > 100000)
            System.err.printf(
                    "WARNING: You have defined a schema that permits very large batches (%.0f max rows (>100K)). This"
                            + " may OOM this stress client, or the server.%n",
                    maxRows);
    }

    static String cql(TableSchema table, String tableName) {
        List<ColumnSchema> keyColumns = table.primaryKey();
        List<ColumnSchema> allColumns = table.columns();
        List<ColumnSchema> valueColumns = table.valueColumns();
        boolean isKeyOnlyTable = valueColumns.isEmpty()
                || (valueColumns.size() == 1 && valueColumns.getFirst().name().isEmpty());
        boolean hasSupportedValue = valueColumns.stream().anyMatch(column -> CqlTypes.isSupported(column.type()));
        return isKeyOnlyTable || !hasSupportedValue
                ? insertCql(table, tableName)
                : updateCql(keyColumns, allColumns, tableName);
    }

    private static String updateCql(List<ColumnSchema> keyColumns, List<ColumnSchema> allColumns, String tableName) {
        StringBuilder sb =
                new StringBuilder("UPDATE ").append(quoteIdentifier(tableName)).append(" SET ");
        StringBuilder pred = new StringBuilder(" WHERE ");
        boolean firstCol = true;
        boolean firstPred = true;
        for (ColumnSchema c : allColumns) {
            if (!CqlTypes.isSupported(c.type())) continue;

            String name = quoteIdentifier(c.name());
            if (keyColumns.contains(c)) {
                if (firstPred) firstPred = false;
                else pred.append(" AND ");
                pred.append(name).append(" = ?");
            } else {
                if (firstCol) firstCol = false;
                else sb.append(',');
                sb.append(name).append(" = ");
                switch (CqlTypes.name(c.type())) {
                    case "SET", "LIST" -> {
                        if (CqlTypes.isFrozen(c.type())) sb.append('?');
                        else sb.append(name).append(" + ?");
                    }
                    case "COUNTER" -> sb.append(name).append(" + ?");
                    default -> sb.append('?');
                }
            }
        }
        return sb.append(pred).toString();
    }

    private static String insertCql(TableSchema table, String tableName) {
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (ColumnSchema column : table.primaryKey()) {
            if (!columns.isEmpty()) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(quoteIdentifier(column.name()));
            values.append('?');
        }
        return "INSERT INTO " + quoteIdentifier(tableName) + " (" + columns + ") values(" + values + ")";
    }

    private static <E> E select(
            E first, String key, String defValue, Map<String, String> map, Function<String, E> builder) {
        String val = map.remove(key);

        if (first != null) return first;
        if (val != null && !val.isBlank()) return builder.apply(val);

        return builder.apply(defValue);
    }

    private static ConsistencyLevel selectConsistency(
            ConsistencyLevel first, String key, ConsistencyLevel defValue, Map<String, String> map) {
        String val = map.remove(key);

        if (first != null) return first;
        if (val != null && !val.isBlank()) return ConsistencyLevel.valueOf(val.toUpperCase(Locale.ROOT));

        return defValue;
    }

    static String quoteIdentifier(String identifier) {
        return LOWERCASE_ALPHANUMERIC.matcher(identifier).matches() ? identifier : '"' + identifier + '"';
    }
}
