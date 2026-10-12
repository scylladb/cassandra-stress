package org.apache.cassandra.stress.operations.userdefined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.CqlType;
import org.apache.cassandra.stress.driver.TableSchema;
import org.junit.jupiter.api.Test;

class ValidatingSchemaQueryTest {
    private static ColumnSchema column(String name, boolean descending) {
        return new ColumnSchema(name, CqlType.of("INT"), descending);
    }

    private static TableSchema table(boolean aDescending, boolean bDescending) {
        return new TableSchema(
                "ks",
                "t",
                List.of(column("pk", false)),
                List.of(column("a", aDescending), column("b", bDescending)),
                List.of());
    }

    private static List<List<String>> cql(TableSchema table) {
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
                cql(table(false, false)));
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
                cql(table(true, true)));
    }

    @Test
    void mixedClusteringSlicesOnlyTheUniformPrefix() {
        assertEquals(
                List.of(
                        List.of("SELECT * FROM t WHERE pk = ?"),
                        List.of("SELECT * FROM t WHERE pk = ? AND (a)<=(?) AND (a)>(?)")),
                cql(table(true, false)));
    }
}
