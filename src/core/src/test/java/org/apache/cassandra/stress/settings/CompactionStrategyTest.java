package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompactionStrategyTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "SizeTieredCompactionStrategy",
                "LeveledCompactionStrategy",
                "TimeWindowCompactionStrategy",
                "DateTieredCompactionStrategy",
                "IncrementalCompactionStrategy",
                "org.apache.cassandra.db.compaction.LeveledCompactionStrategy"
            })
    void returnsTheNameAsGiven(String name) {
        assertEquals(name, CompactionStrategy.validate(name));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "NoSuchCompactionStrategy",
                "java.lang.String",
                "org.apache.cassandra.locator.LeveledCompactionStrategy"
            })
    void rejectsUnknownStrategies(String name) {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> CompactionStrategy.validate(name));
        assertEquals("Invalid compaction strategy: " + name, e.getMessage());
    }
}
