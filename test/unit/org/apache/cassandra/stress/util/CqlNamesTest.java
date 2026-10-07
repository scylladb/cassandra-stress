package org.apache.cassandra.stress.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CqlNamesTest {
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "ks1   | CREATE KEYSPACE ks1 WITH replication = {'class': 'NetworkTopologyStrategy',"
                        + " 'replication_factor': 3};",
                "ks1   | create keyspace if not exists Ks1 with replication = {}",
                "MyKs  | CREATE KEYSPACE \"MyKs\" WITH replication = {}",
            })
    void readsTheKeyspace(String expected, String cql) {
        assertEquals(expected, CqlNames.keyspaceOf(cql));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "standard1 | CREATE TABLE standard1 (key blob PRIMARY KEY)",
                "t1        | CREATE TABLE IF NOT EXISTS ks1.T1 (k int PRIMARY KEY)",
                "Tbl       | create table \"Ks\".\"Tbl\"(k int primary key)",
                "t1        | CREATE COLUMNFAMILY t1 (k int PRIMARY KEY)",
            })
    void readsTheTable(String expected, String cql) {
        assertEquals(expected, CqlNames.tableOf(cql));
    }

    @Test
    void readsNamesAcrossWhitespace() {
        assertEquals("ks1", CqlNames.keyspaceOf("CREATE  KEYSPACE\n\tIF  NOT EXISTS\n ks1 WITH durable_writes = true"));
        assertEquals("t1", CqlNames.tableOf("CREATE  TABLE\n  IF NOT  EXISTS  t1  (k int PRIMARY KEY)"));
    }

    @Test
    void skipsLeadingComments() {
        assertEquals(
                "ks1",
                CqlNames.keyspaceOf(
                        "-- keyspace for the test\n// second line\nCREATE KEYSPACE ks1 WITH replication = {}"));
        assertEquals(
                "t1", CqlNames.tableOf("/* first\n line */ /* second */\nCREATE TABLE ks1.t1 (k int PRIMARY KEY)"));
    }

    @Test
    void readsQuotedNamesWithEscapedQuotes() {
        assertEquals("a\"b", CqlNames.tableOf("CREATE TABLE \"a\"\"b\" (k int PRIMARY KEY)"));
    }

    @Test
    void rejectsOtherStatements() {
        assertThrows(IllegalArgumentException.class, () -> CqlNames.tableOf("SELECT * FROM t1"));
        assertThrows(IllegalArgumentException.class, () -> CqlNames.keyspaceOf("CREATE TABLE t1 (k int PRIMARY KEY)"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "standard1 | standard1",
                "c_1       | c_1",
                "order     | \"order\"",
                "token     | \"token\"",
                "MyTable   | \"MyTable\"",
                "1col      | \"1col\"",
                "a\"b      | \"a\"\"b\"",
            })
    void quotesNamesThatCqlWouldNotReadAsTheyAre(String name, String expected) {
        assertEquals(expected, CqlNames.quote(name));
    }
}
