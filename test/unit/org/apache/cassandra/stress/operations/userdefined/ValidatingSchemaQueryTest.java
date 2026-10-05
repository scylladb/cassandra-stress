package org.apache.cassandra.stress.operations.userdefined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.internal.core.metadata.schema.DefaultColumnMetadata;
import com.datastax.oss.driver.internal.core.metadata.schema.DefaultTableMetadata;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidatingSchemaQueryTest {
    private static final CqlIdentifier KEYSPACE = CqlIdentifier.fromInternal("ks");
    private static final CqlIdentifier TABLE = CqlIdentifier.fromInternal("t");

    private static ColumnMetadata column(String name) {
        return new DefaultColumnMetadata(KEYSPACE, TABLE, CqlIdentifier.fromInternal(name), DataTypes.INT, false);
    }

    private static TableMetadata table(ClusteringOrder a, ClusteringOrder b) {
        ColumnMetadata pk = column("pk");
        Map<ColumnMetadata, ClusteringOrder> clustering = new LinkedHashMap<>();
        clustering.put(column("a"), a);
        clustering.put(column("b"), b);
        Map<CqlIdentifier, ColumnMetadata> columns = new LinkedHashMap<>();
        columns.put(pk.getName(), pk);
        clustering.keySet().forEach(c -> columns.put(c.getName(), c));
        return new DefaultTableMetadata(
                KEYSPACE, TABLE, UUID.randomUUID(), false, false, List.of(pk), clustering, columns, Map.of(), Map.of());
    }

    private static List<List<String>> cql(TableMetadata table) {
        return ValidatingSchemaQuery.queries(table).stream()
                .map(slices ->
                        slices.stream().map(ValidatingSchemaQuery.Slice::cql).toList())
                .toList();
    }

    @Test
    void ascendingClusteringSlicesUpward() {
        assertEquals(
                List.of(
                        List.of("SELECT * FROM t WHERE pk = ?"),
                        List.of("SELECT * FROM t WHERE pk = ? AND (a)>=(?) AND (a)<(?)"),
                        List.of(
                                "SELECT * FROM t WHERE pk = ? AND (a,b)>=(?,?) AND (a,b)<=(?,?)",
                                "SELECT * FROM t WHERE pk = ? AND (a,b)>=(?,?) AND (a,b)<(?,?)",
                                "SELECT * FROM t WHERE pk = ? AND (a,b)>(?,?) AND (a,b)<=(?,?)",
                                "SELECT * FROM t WHERE pk = ? AND (a,b)>(?,?) AND (a,b)<(?,?)")),
                cql(table(ClusteringOrder.ASC, ClusteringOrder.ASC)));
    }

    @Test
    void descendingClusteringSlicesDownward() {
        assertEquals(
                List.of(
                        List.of("SELECT * FROM t WHERE pk = ?"),
                        List.of("SELECT * FROM t WHERE pk = ? AND (a)<=(?) AND (a)>(?)"),
                        List.of(
                                "SELECT * FROM t WHERE pk = ? AND (a,b)<=(?,?) AND (a,b)>=(?,?)",
                                "SELECT * FROM t WHERE pk = ? AND (a,b)<=(?,?) AND (a,b)>(?,?)",
                                "SELECT * FROM t WHERE pk = ? AND (a,b)<(?,?) AND (a,b)>=(?,?)",
                                "SELECT * FROM t WHERE pk = ? AND (a,b)<(?,?) AND (a,b)>(?,?)")),
                cql(table(ClusteringOrder.DESC, ClusteringOrder.DESC)));
    }

    @Test
    void mixedClusteringSlicesOnlyTheUniformPrefix() {
        assertEquals(
                List.of(
                        List.of("SELECT * FROM t WHERE pk = ?"),
                        List.of("SELECT * FROM t WHERE pk = ? AND (a)<=(?) AND (a)>(?)")),
                cql(table(ClusteringOrder.DESC, ClusteringOrder.ASC)));
    }
}
