package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CliOutputTest {
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private PrintStream previous;

    @BeforeEach
    void captureStdout() {
        previous = System.out;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(previous);
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    void helpListsTheCommandsAndOptions() {
        assertNull(StressSettings.parse(new String[] {"help"}));
        String help = output();
        assertTrue(help.contains("Usage:"), help);
        for (Command command : Command.values()) {
            assertTrue(help.contains(command.toString().toLowerCase(java.util.Locale.ROOT)), command.toString());
        }
        assertFalse(help.contains("legacy"));
    }

    static Stream<String> helpTopics() {
        return Stream.concat(
                Arrays.stream(Command.values()).map(c -> c.toString().toLowerCase(java.util.Locale.ROOT)),
                Arrays.stream(CliOption.values()).map(o -> "-" + o.toString().toLowerCase(java.util.Locale.ROOT)));
    }

    @ParameterizedTest
    @MethodSource("helpTopics")
    void helpPrintsEachTopic(String topic) {
        assertNull(StressSettings.parse(new String[] {"help", topic}));
        assertFalse(output().isBlank(), topic);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "gauss(1..100,5)",
                "uniform(1..1000)",
                "fixed(3)",
                "exp(1..100)",
                "extreme(1..100,2)",
                "seq(1..10)"
            })
    void printShowsTheDistribution(String distribution) {
        assertNull(StressSettings.parse(new String[] {"print", "dist=" + distribution}));
        assertTrue(output().contains("% of samples"), output());
    }

    @Test
    void helpForVersionPrintsItsDescription() {
        assertNull(StressSettings.parse(new String[] {"help", "version"}));
        assertTrue(output().contains("Print the version of cassandra stress"), output());
    }

    @Test
    void versionPrintsThreeLines() {
        assertNull(StressSettings.parse(new String[] {"version"}));
        assertTrue(output().startsWith("Version: "), output());
    }

    @ParameterizedTest
    @EnumSource(
            value = Command.class,
            names = {"READ", "WRITE", "COUNTER_WRITE", "COUNTER_READ"})
    void printSettingsDescribesEveryGroup(Command command) {
        StressSettings settings = StressSettings.parse(new String[] {
            command.toString().toLowerCase(java.util.Locale.ROOT),
            "n=10",
            "-rate",
            "threads=2",
            "fixed=10/s",
            "-errors",
            "retries=3",
            "delay-policy=linear",
            "min-delay-ms=1",
            "-insert",
            "visits=fixed(2)",
            "-pop",
            "dist=gauss(1..100,5)",
            "-transport",
            "truststore=/tmp/ts.jks",
            "truststore-password=p",
            "-node",
            "10.0.0.1,10.0.0.2",
            "datacenter=dc1",
            "-tokenrange",
            "wrap"
        });
        settings.printSettings(new MultiResultLogger(System.out));
        String printed = output();
        for (String group : new String[] {
            "Command:",
            "Rate:",
            "Population:",
            "Insert:",
            "Columns:",
            "Errors:",
            "Log:",
            "Mode:",
            "Node:",
            "Schema:",
            "Transport:",
            "Port:",
            "Send To Daemon:",
            "Graph:",
            "TokenRange:"
        }) {
            assertTrue(printed.contains(group), group + " missing in\n" + printed);
        }
    }
}
