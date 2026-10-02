package org.apache.cassandra.stress.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CqlNamesTest
{
    @Test
    public void readsPlainKeyspace()
    {
        assertEquals("ks1", CqlNames.keyspaceOf("CREATE KEYSPACE ks1 WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 3};"));
    }

    @Test
    public void readsKeyspaceWithIfNotExistsInAnyCase()
    {
        assertEquals("ks1", CqlNames.keyspaceOf("create keyspace if not exists Ks1 with replication = {}"));
    }

    @Test
    public void readsKeyspaceAcrossWhitespace()
    {
        assertEquals("ks1", CqlNames.keyspaceOf("CREATE  KEYSPACE\n\tIF  NOT EXISTS\n ks1 WITH durable_writes = true"));
    }

    @Test
    public void keepsQuotedKeyspaceCase()
    {
        assertEquals("MyKs", CqlNames.keyspaceOf("CREATE KEYSPACE \"MyKs\" WITH replication = {}"));
    }

    @Test
    public void readsPlainTable()
    {
        assertEquals("standard1", CqlNames.tableOf("CREATE TABLE standard1 (key blob PRIMARY KEY)"));
    }

    @Test
    public void readsQualifiedTableWithIfNotExists()
    {
        assertEquals("t1", CqlNames.tableOf("CREATE TABLE IF NOT EXISTS ks1.T1 (k int PRIMARY KEY)"));
    }

    @Test
    public void readsQuotedQualifiedTable()
    {
        assertEquals("Tbl", CqlNames.tableOf("create table \"Ks\".\"Tbl\"(k int primary key)"));
    }

    @Test
    public void readsTableAcrossWhitespace()
    {
        assertEquals("t1", CqlNames.tableOf("CREATE  TABLE\n  IF NOT  EXISTS  t1  (k int PRIMARY KEY)"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOtherStatements()
    {
        CqlNames.tableOf("SELECT * FROM t1");
    }
}
