package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SettingsSchemaTest {
    private static StressSettings settings(String names) {
        return StressSettings.parse(new String[] {"write", "n=1", "-col", "names=" + names});
    }

    @Test
    void quotesEachColumnNameOfTheStandardTable() {
        StressSettings settings = settings("plain,C0,order,a\"b");

        assertEquals("""
                CREATE TABLE IF NOT EXISTS standard1 (key blob PRIMARY KEY\s
                , "C0" blob
                , "a""b" blob
                , "order" blob
                , plain blob) WITH compression = {};
                """, settings.schema.createStandard1StatementCQL3(settings));
    }

    @Test
    void quotesEachColumnNameOfTheCounterTable() {
        StressSettings settings = settings("plain,C0,order,a\"b");

        assertEquals("""
                CREATE TABLE IF NOT EXISTS counter1 (key blob PRIMARY KEY,
                , "C0" counter
                , "a""b" counter
                , "order" counter
                , plain counter) WITH compression = {};
                """, settings.schema.createCounter1StatementCQL3(settings));
    }
}
