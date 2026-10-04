package org.apache.cassandra.stress;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StressProfileTest
{
    private static final String PROFILE = """
        keyspace: test_keyspace
        table: test_table
        table_definition: |
          CREATE TABLE test_table (key blob PRIMARY KEY, C0 blob, C1 blob)
        columnspec:
          - name: key
            size: fixed(10)
          - name: C0
            size: fixed(20)
        insert:
          partitions: fixed(1)
        queries:
          read:
            cql: SELECT * FROM test_table WHERE key = ?
            fields: samerow
        """;

    @TempDir
    Path dir;

    private StressProfile load(String yaml) throws Exception
    {
        Path file = Files.writeString(dir.resolve("profile.yaml"), yaml);
        return StressProfile.load(file.toUri());
    }

    @Test
    void loadsKeyspaceAndTableNames() throws Exception
    {
        StressProfile profile = load(PROFILE);
        assertEquals("test_keyspace", profile.keyspaceName);
        assertEquals("test_table", profile.tableName);
        assertEquals("test_keyspace.test_table", profile.specName);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "keyspace: test_keyspace | keyspace name is required in yaml file",
        "table: test_table       | table name is required in yaml file",
    })
    void rejectsAProfileWithoutARequiredName(String line, String message)
    {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> load(PROFILE.replace(line + "\n", "")));
        assertEquals(message, e.getMessage());
    }

    @Test
    void rejectsAProfileWithoutQueries()
    {
        String yaml = PROFILE.substring(0, PROFILE.indexOf("queries:"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> load(yaml));
        assertEquals("queries map is required in yaml file", e.getMessage());
    }

    @Test
    void lowerCasesMapKeysAndKeepsValues()
    {
        Map<String, String> map = new HashMap<>();
        map.put("UpperCase", "a");
        map.put("lowercase", "b");
        map.put("NullValue", null);

        StressProfile.lowerCase(map);

        Map<String, String> expected = new HashMap<>();
        expected.put("uppercase", "a");
        expected.put("lowercase", "b");
        expected.put("nullvalue", null);
        assertEquals(expected, map);
    }
}
