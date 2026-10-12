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

        assertEquals(
                "CREATE TABLE IF NOT EXISTS standard1 (key blob PRIMARY KEY \n"
                        + ", \"C0\" blob\n"
                        + ", \"a\"\"b\" blob\n"
                        + ", \"order\" blob\n"
                        + ", plain blob) WITH compression = {};\n",
                settings.schema.createStandard1StatementCQL3(settings));
    }

    @Test
    void quotesEachColumnNameOfTheCounterTable() {
        StressSettings settings = settings("plain,C0,order,a\"b");

        assertEquals(
                "CREATE TABLE IF NOT EXISTS counter1 (key blob PRIMARY KEY,\n"
                        + ", \"C0\" counter\n"
                        + ", \"a\"\"b\" counter\n"
                        + ", \"order\" counter\n"
                        + ", plain counter) WITH compression = {};\n",
                settings.schema.createCounter1StatementCQL3(settings));
    }
}
