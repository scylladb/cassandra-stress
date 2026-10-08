package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StressSettingsTest {
    @Test
    void legacyCommandIsRejected() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> StressSettings.parse(new String[] {"legacy", "-o", "INSERT"}));
        assertEquals("Command legacy was removed. Run cassandra-stress help to see the commands.", e.getMessage());
    }

    @Test
    void userCommandIsRejectedWithSendTo() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> StressSettings.parse(new String[] {
                    "user", "profile=examples/cqlstress-example.yaml", "ops(insert=1)", "-send-to", "127.0.0.1"
                }));
        assertEquals(
                "-send-to runs the predefined commands only. Run the user command without -send-to.", e.getMessage());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "write n=10 -rate threads=4|write n=10 -rate threads=4",
                "write n = 10 -rate threads =4|write n=10 -rate threads=4",
                "write n=10 -pop dist=gauss( 1..10 , 5 )|write n=10 -pop dist=gauss(1..10,5)",
                "write n=10 -col names=a , b|write n=10 -col names=a,b",
                "write   n=10 -log interval=1s|write n=10 -log interval=1s"
            })
    void repairsTheSpacesAroundDelimiters(String input, String expected) {
        assertEquals(List.of(expected.split(" ")), List.of(StressSettings.repairParams(input.split(" "))));
    }

    @Test
    void repairsALongRunOfSpacesInLinearTime() {
        String spaces = " ".repeat(200_000);
        assertEquals(
                List.of("write", "n=10"),
                List.of(StressSettings.repairParams(new String[] {"write" + spaces + "n" + spaces + "=10"})));
    }

    @Test
    void readsTheConsistencyLevelInATurkishLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            StressSettings settings = StressSettings.parse(new String[] {"write", "n=10", "cl=serial"});
            assertEquals(ConsistencyLevel.SERIAL, settings.command.consistencyLevel);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "write n=10 -rate threads=4 auto|Invalid -rate options provided, see output for valid options",
                "write n=10 -rate throttle=100/s|Invalid -rate options provided, see output for valid options",
                "write n=10 -mode native|Invalid -mode options provided, see output for valid options",
                "write n=10 -pop seq=1..10 dist=gauss(1..10)|Invalid -pop options provided, see output for valid"
                        + " options",
                "write n=10 -col n=2 names=a,b|Invalid -col options provided, see output for valid options",
                "write n=10 -graph title=x|Invalid -graph options provided, see output for valid options",
                "write n=10 duration=1m|Invalid WRITE options provided, see output for valid options",
                "mixed n=10 duration=1m|Invalid MIXED options provided, see output for valid options",
                "user n=10 duration=1m|Invalid USER options provided, see output for valid options",
                "print|Invalid print options provided, see output for valid options",
                "write n=10 -send-to a b|Invalid -sendto specifier: [a, b]",
                "write n=10 -send-to host:x|Invalid port: x",
                "write n=10 -bogus 1|Error processing command line arguments. The following were ignored:",
            })
    void invalidOptionsThrowWithTheirHelp(String command, String message) {
        InvalidSettingsException e =
                assertThrows(InvalidSettingsException.class, () -> StressSettings.parse(command.split(" ")));
        assertEquals(message, e.getMessage().lines().findFirst().orElseThrow());
    }

    @Test
    void anEmptyCommandLineThrows() {
        InvalidSettingsException e =
                assertThrows(InvalidSettingsException.class, () -> StressSettings.parse(new String[0]));
        assertEquals("No command provided", e.getMessage());
    }

    private static Set<Path> graphLogs() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(f -> f.getFileName().toString().startsWith("cassandra-stress"))
                    .collect(Collectors.toSet());
        }
    }

    @Test
    void deletesTheGraphLogWhenAnArgumentIsLeftOver() throws IOException {
        Set<Path> before = graphLogs();
        assertThrows(
                InvalidSettingsException.class,
                () -> StressSettings.parse(new String[] {"write", "n=1", "-graph", "file=a.html", "-bogus"}));
        assertEquals(before, graphLogs());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "write n=1                           | standard1",
                "read n=1                            | standard1",
                "counter_write n=1                   | counter1",
                "mixed ratio(write=1,read=1) n=1     | standard1",
            })
    void truncatesOnlyTheTablesThatTheCommandUses(String command, String tables) {
        SettingsCommandPreDefined settings =
                (SettingsCommandPreDefined) StressSettings.parse(command.split(" ")).command;
        assertEquals(List.of(tables.split(",")), List.of(settings.tables()));
    }
}
