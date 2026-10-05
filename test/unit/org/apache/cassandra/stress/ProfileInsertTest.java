package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.internal.core.metadata.schema.DefaultColumnMetadata;
import com.datastax.oss.driver.internal.core.metadata.schema.DefaultTableMetadata;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.values.Generator;
import org.apache.cassandra.stress.generate.values.GeneratorConfig;
import org.apache.cassandra.stress.generate.values.Integers;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProfileInsertTest {
    private static final CqlIdentifier KEYSPACE = CqlIdentifier.fromInternal("ks");
    private static final CqlIdentifier TABLE = CqlIdentifier.fromInternal("t");

    private static ColumnMetadata column(String name, DataType type) {
        return new DefaultColumnMetadata(KEYSPACE, TABLE, CqlIdentifier.fromInternal(name), type, false);
    }

    private static TableMetadata table(ColumnMetadata... values) {
        ColumnMetadata pk = column("pk", DataTypes.INT);
        ColumnMetadata ck = column("ck", DataTypes.INT);
        Map<CqlIdentifier, ColumnMetadata> columns = new LinkedHashMap<>();
        columns.put(pk.getName(), pk);
        columns.put(ck.getName(), ck);
        for (ColumnMetadata value : values) columns.put(value.getName(), value);
        return new DefaultTableMetadata(
                KEYSPACE,
                TABLE,
                UUID.randomUUID(),
                false,
                false,
                List.of(pk),
                Map.of(ck, ClusteringOrder.ASC),
                columns,
                Map.of(),
                Map.of());
    }

    private static PartitionGenerator generator() {
        GeneratorConfig config = new GeneratorConfig("seed", null, null, null);
        List<Generator> pk = List.of(new Integers("pk", config));
        return new PartitionGenerator(pk, List.of(), List.of(), PartitionGenerator.Order.ARBITRARY);
    }

    private static ProfileInsert insert(TableMetadata table, Map<String, String> options) {
        return ProfileInsert.of(table, "t", options, generator(), StressSettings.parse(new String[] {"write", "n=10"}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"consistencyLevel", "consistencylevel", "CONSISTENCYLEVEL"})
    void readsTheConsistencyLevelsOfTheInsertBlock(String key) {
        ProfileInsert spec = insert(
                table(column("v", DataTypes.TEXT)),
                Map.of(key, "LOCAL_QUORUM", "serialConsistencyLevel", "LOCAL_SERIAL", "batchtype", "UNLOGGED"));
        assertEquals(ConsistencyLevel.LOCAL_QUORUM, spec.consistencyLevel());
        assertEquals(ConsistencyLevel.LOCAL_SERIAL, spec.serialConsistencyLevel());
        assertEquals(DefaultBatchType.UNLOGGED, spec.batchType());
    }

    @Test
    void rejectsAnUnknownInsertOption() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> insert(table(column("v", DataTypes.TEXT)), Map.of("bogus", "1")));
        assertEquals("Unrecognised insert option(s): {bogus=1}", e.getMessage());
    }

    @Test
    void insertsTheKeyWhenEveryValueColumnIsUnsupported() {
        assertEquals(
                "INSERT INTO t (pk, ck) values(?, ?)",
                ProfileInsert.cql(table(column("m", DataTypes.mapOf(DataTypes.TEXT, DataTypes.INT))), "t"));
    }

    @Test
    void updatesEachSupportedValueColumn() {
        String cql = ProfileInsert.cql(
                table(
                        column("tags", DataTypes.setOf(DataTypes.TEXT)),
                        column("nums", DataTypes.frozenListOf(DataTypes.INT)),
                        column("m", DataTypes.mapOf(DataTypes.TEXT, DataTypes.INT))),
                "t");
        assertTrue(cql.startsWith("UPDATE t SET "), cql);
        assertTrue(cql.contains("tags = tags + ?"), cql);
        assertTrue(cql.contains("nums = ?"), cql);
        assertTrue(!cql.contains("m ="), cql);
        assertTrue(cql.endsWith(" WHERE pk = ? AND ck = ?") || cql.endsWith(" WHERE ck = ? AND pk = ?"), cql);
    }

    @Test
    void addsToACounter() {
        assertTrue(
                ProfileInsert.cql(table(column("hits", DataTypes.COUNTER)), "t").contains("hits = hits + ?"));
    }
}
