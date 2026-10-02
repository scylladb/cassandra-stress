package org.apache.cassandra.stress.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CompactionStrategyTest
{
    private static void assertRejected(String name)
    {
        try
        {
            CompactionStrategy.validate(name);
            fail(name + " must be rejected");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Invalid compaction strategy: " + name, e.getMessage());
        }
    }

    @Test
    public void returnsShortNamesAsGiven()
    {
        for (String name : new String[]{ "SizeTieredCompactionStrategy", "LeveledCompactionStrategy", "TimeWindowCompactionStrategy",
                                         "DateTieredCompactionStrategy", "IncrementalCompactionStrategy" })
            assertEquals(name, CompactionStrategy.validate(name));
    }

    @Test
    public void returnsFullNamesAsGiven()
    {
        assertEquals("org.apache.cassandra.db.compaction.LeveledCompactionStrategy",
                     CompactionStrategy.validate("org.apache.cassandra.db.compaction.LeveledCompactionStrategy"));
    }

    @Test
    public void rejectsUnknownStrategies()
    {
        assertRejected("NoSuchCompactionStrategy");
        assertRejected("java.lang.String");
        assertRejected("org.apache.cassandra.locator.LeveledCompactionStrategy");
    }
}
