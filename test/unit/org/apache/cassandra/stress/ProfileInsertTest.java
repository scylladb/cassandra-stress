package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.cassandra.stress.driver.BatchType;
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.CqlType;
import org.apache.cassandra.stress.driver.TableSchema;
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
    private static ColumnSchema column(String name, CqlType type) {
        return new ColumnSchema(name, type);
    }

    private static CqlType of(String name) {
        return CqlType.of(name);
    }

    private static CqlType collection(String name, boolean frozen, CqlType... elements) {
        return new CqlType(name, List.of(elements), frozen);
    }

    private static TableSchema table(ColumnSchema... values) {
        return new TableSchema(
                "ks", "t", List.of(column("pk", of("INT"))), List.of(column("ck", of("INT"))), List.of(values));
    }

    private static PartitionGenerator generator() {
        GeneratorConfig config = new GeneratorConfig("seed", null, null, null);
        List<Generator> pk = List.of(new Integers("pk", config));
        return new PartitionGenerator(pk, List.of(), List.of(), PartitionGenerator.Order.ARBITRARY);
    }

    private static ProfileInsert insert(TableSchema table, Map<String, String> options) {
        return ProfileInsert.of(table, "t", options, generator(), StressSettings.parse(new String[] {"write", "n=10"}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"consistencyLevel", "consistencylevel", "CONSISTENCYLEVEL"})
    void readsTheConsistencyLevelsOfTheInsertBlock(String key) {
        ProfileInsert spec = insert(
                table(column("v", of("TEXT"))),
                Map.of(key, "LOCAL_QUORUM", "serialConsistencyLevel", "LOCAL_SERIAL", "batchtype", "UNLOGGED"));
        assertEquals(ConsistencyLevel.LOCAL_QUORUM, spec.consistencyLevel());
        assertEquals(ConsistencyLevel.LOCAL_SERIAL, spec.serialConsistencyLevel());
        assertEquals(BatchType.UNLOGGED, spec.batchType());
    }

    @Test
    void rejectsAnUnknownInsertOption() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> insert(table(column("v", of("TEXT"))), Map.of("bogus", "1")));
        assertEquals("Unrecognised insert option(s): {bogus=1}", e.getMessage());
    }

    @Test
    void insertsTheKeyWhenEveryValueColumnIsUnsupported() {
        assertEquals(
                "INSERT INTO t (pk, ck) values(?, ?)",
                ProfileInsert.cql(table(column("m", collection("MAP", false, of("TEXT"), of("INT")))), "t"));
    }

    @Test
    void updatesEachSupportedValueColumn() {
        String cql = ProfileInsert.cql(
                table(
                        column("tags", collection("SET", false, of("TEXT"))),
                        column("nums", collection("LIST", true, of("INT"))),
                        column("m", collection("MAP", false, of("TEXT"), of("INT")))),
                "t");
        assertTrue(cql.startsWith("UPDATE t SET "), cql);
        assertTrue(cql.contains("tags = tags + ?"), cql);
        assertTrue(cql.contains("nums = ?"), cql);
        assertTrue(!cql.contains("m ="), cql);
        assertTrue(cql.endsWith(" WHERE pk = ? AND ck = ?") || cql.endsWith(" WHERE ck = ? AND pk = ?"), cql);
    }

    @Test
    void addsToACounter() {
        assertTrue(ProfileInsert.cql(table(column("hits", of("COUNTER"))), "t").contains("hits = hits + ?"));
    }
}
