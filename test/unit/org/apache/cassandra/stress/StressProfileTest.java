package org.apache.cassandra.stress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StressProfileTest
{
    private static final String PROFILE = String.join("\n",
        "keyspace: test_keyspace",
        "table: test_table",
        "table_definition: |",
        "  CREATE TABLE test_table (key blob PRIMARY KEY, C0 blob, C1 blob)",
        "columnspec:",
        "  - name: key",
        "    size: fixed(10)",
        "  - name: C0",
        "    size: fixed(20)",
        "insert:",
        "  partitions: fixed(1)",
        "queries:",
        "  read:",
        "    cql: SELECT * FROM test_table WHERE key = ?",
        "    fields: samerow",
        "");

    @Test
    public void loadsKeyspaceAndTableNames() throws IOException
    {
        Path file = Files.createTempFile("stress-profile", ".yaml");
        try
        {
            Files.writeString(file, PROFILE);
            StressProfile profile = StressProfile.load(file.toUri());
            assertEquals("test_keyspace", profile.keyspaceName);
            assertEquals("test_table", profile.tableName);
        }
        finally
        {
            Files.delete(file);
        }
    }

    @Test
    public void lowerCasesMapKeys()
    {
        Map<String, String> map = new HashMap<>();
        map.put("UpperCase", "a");
        map.put("lowercase", "b");

        StressProfile.lowerCase(map);

        Map<String, String> expected = new HashMap<>();
        expected.put("uppercase", "a");
        expected.put("lowercase", "b");
        assertEquals(expected, map);
    }
}
