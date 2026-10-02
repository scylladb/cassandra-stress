package org.apache.cassandra.stress.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ReplicationStrategyTest
{
    private static void assertRejected(String name)
    {
        try
        {
            ReplicationStrategy.validate(name);
            fail(name + " must be rejected");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Invalid replication strategy: " + name, e.getMessage());
        }
    }

    @Test
    public void acceptsShortNames()
    {
        assertEquals("org.apache.cassandra.locator.NetworkTopologyStrategy", ReplicationStrategy.validate("NetworkTopologyStrategy"));
        assertEquals("org.apache.cassandra.locator.EverywhereStrategy", ReplicationStrategy.validate("EverywhereStrategy"));
    }

    @Test
    public void acceptsFullNames()
    {
        assertEquals("org.apache.cassandra.locator.NetworkTopologyStrategy", ReplicationStrategy.validate("org.apache.cassandra.locator.NetworkTopologyStrategy"));
    }

    @Test
    public void rejectsRemovedStrategies()
    {
        assertRejected("SimpleStrategy");
        assertRejected("org.apache.cassandra.locator.SimpleStrategy");
        assertRejected("LocalStrategy");
        assertRejected("OldNetworkTopologyStrategy");
        assertRejected("java.lang.String");
    }
}
