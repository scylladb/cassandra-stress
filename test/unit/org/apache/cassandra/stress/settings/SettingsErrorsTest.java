package org.apache.cassandra.stress.settings;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsErrorsTest
{
    private static SettingsErrors parse(String... params)
    {
        return SettingsErrors.get(new HashMap<>(Map.of("-errors", params)));
    }

    private static SettingsErrors.Options options(String... params)
    {
        SettingsErrors.Options options = new SettingsErrors.Options();
        for (String param : params)
            assertTrue(options.accept(param), param);
        return options;
    }

    @Test
    void defaultsToTenTriesAndNoDelay()
    {
        SettingsErrors errors = SettingsErrors.get(new HashMap<>());
        assertEquals(10, errors.tries);
        assertFalse(errors.ignore);
        assertFalse(errors.failFast);
        assertEquals(Duration.ZERO, errors.nextDelay(3));
    }

    @Test
    void readsTheFlags()
    {
        SettingsErrors errors = parse("ignore", "fail-fast", "skip-read-validation", "skip-unsupported-columns", "retries=2");
        assertTrue(errors.ignore);
        assertTrue(errors.failFast);
        assertTrue(errors.skipReadValidation);
        assertTrue(errors.skipUnsupportedColumns);
        assertEquals(3, errors.tries);
    }

    @ParameterizedTest
    @ValueSource(strings = { "constant", "linear", "exponential" })
    void delaysStayWithinTheBounds(String policy)
    {
        SettingsErrors errors = parse("retries=6", "delay-policy=" + policy, "min-delay-ms=10", "max-delay-ms=200");
        for (int tries = 0; tries < errors.tries; tries++)
        {
            long delay = errors.nextDelay(tries).toMillis();
            assertTrue(delay >= 10 && delay <= 200, policy + " gave " + delay);
        }
    }

    @Test
    void exponentialDelayGrowsUntilTheMaximum()
    {
        SettingsErrors errors = parse("retries=9", "delay-policy=exponential", "min-delay-ms=1", "max-delay-ms=50");
        assertEquals(50, errors.nextDelay(9).toMillis());
    }

    @Test
    void rejectsAMinimumAboveTheMaximum()
    {
        assertFalse(options("min-delay-ms=10", "max-delay-ms=5").happy());
    }

    @Test
    void rejectsExponentialWithoutAMinimum()
    {
        assertFalse(options("delay-policy=exponential").happy());
        assertTrue(options("delay-policy=exponential", "min-delay-ms=1").happy());
    }
}
