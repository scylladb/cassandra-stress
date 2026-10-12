package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.junit.jupiter.api.Test;

class StressActionTest {
    interface Answer {
        Object answer(String method) throws Throwable;
    }

    private static StressClient client(Answer answer) {
        return (StressClient) Proxy.newProxyInstance(
                StressActionTest.class.getClassLoader(),
                new Class<?>[] {StressClient.class},
                (proxy, method, args) ->
                        "disconnect".equals(method.getName()) ? null : answer.answer(method.getName()));
    }

    private static List<String> lastTwoLines(ByteArrayOutputStream printed) {
        List<String> lines = printed.toString(StandardCharsets.UTF_8).lines().toList();
        return lines.subList(lines.size() - 2, lines.size());
    }

    private static ByteArrayOutputStream run(StressClient client, Class<? extends Throwable> thrown, String... args) {
        StressSettings settings = StressSettings.parse(args);
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        MultiResultLogger output = new MultiResultLogger(new PrintStream(printed, true, StandardCharsets.UTF_8));
        settings.setOutput(output);
        settings.setClient(client);

        assertThrows(thrown, () -> new StressAction(settings, output).run());
        return printed;
    }

    @Test
    void namesAnErrorThatStopsTheRun() {
        ByteArrayOutputStream printed = run(
                client(method -> {
                    throw new AssertionError("schema check failed");
                }),
                AssertionError.class,
                "write",
                "n=1");

        assertEquals(List.of("Stress action failed: schema check failed", "FAILURE"), lastTwoLines(printed));
    }

    @Test
    void namesAFailedOperationAndNotACancelledRun() {
        ByteArrayOutputStream printed = run(
                client(method -> {
                    if ("execute".equals(method)) {
                        return null;
                    }
                    throw new IllegalStateException("the node is down");
                }),
                RuntimeException.class,
                "write",
                "n=10",
                "no-warmup",
                "-rate",
                "threads=1");

        assertEquals(List.of("Stress action failed: An operation failed", "FAILURE"), lastTwoLines(printed));
    }
}
