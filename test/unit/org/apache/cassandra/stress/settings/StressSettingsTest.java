package org.apache.cassandra.stress.settings;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import org.apache.cassandra.stress.util.ConsistencyLevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StressSettingsTest
{
    @Test
    void legacyCommandIsRejected()
    {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> StressSettings.parse(new String[]{ "legacy", "-o", "INSERT" }));
        assertEquals("Command legacy was removed. Run cassandra-stress help to see the commands.", e.getMessage());
    }

    @Test
    void userCommandIsRejectedWithSendTo()
    {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> StressSettings.parse(new String[]{ "user", "profile=examples/cqlstress-example.yaml", "ops(insert=1)", "-send-to", "127.0.0.1" }));
        assertEquals("-send-to runs the predefined commands only. Run the user command without -send-to.", e.getMessage());
    }

    @Test
    void readsTheConsistencyLevelInATurkishLocale()
    {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try
        {
            StressSettings settings = StressSettings.parse(new String[]{ "write", "n=10", "cl=serial" });
            assertEquals(ConsistencyLevel.SERIAL, settings.command.consistencyLevel);
        }
        finally
        {
            Locale.setDefault(previous);
        }
    }
}
