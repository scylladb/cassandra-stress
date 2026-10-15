package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsModeTest {
    private static SettingsMode parse(String... params) {
        return SettingsMode.get(new HashMap<>(Map.of("-mode", params)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"simplenative", "SIMPLENATIVE", "SimpleNative"})
    void simpleNativeModeIsRejectedInAnyCase(String mode) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("cql3", mode));
        assertEquals("Mode simplenative was removed. Use -mode cql3 native or -mode cql3 4x.", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"thrift", "THRIFT", "smart", "native4x", "nativex", "compresion=lz4"})
    void anUnknownTokenIsAnInvalidParameter(String token) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("cql3", token));
        assertEquals("Invalid parameter " + token, e.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
        "native, JAVA_DRIVER_NATIVE",
        "NATIVE, JAVA_DRIVER_NATIVE",
        "4x, JAVA_DRIVER4_NATIVE",
        "4X, JAVA_DRIVER4_NATIVE"
    })
    void theDriverNameIsReadInAnyCase(String driver, ConnectionAPI api) {
        assertEquals(api, parse("cql3", driver).api);
    }

    @ParameterizedTest
    @ValueSource(strings = {"native", "4x"})
    void acceptsEachDriverName(String driver) {
        assertEquals(ConnectionStyle.CQL_PREPARED, parse("cql3", driver).style);
    }

    @Test
    void acceptsCql3Alone() {
        assertEquals(ConnectionStyle.CQL_PREPARED, parse("cql3").style);
    }

    @Test
    void defaultsToPreparedStatements() {
        assertEquals(ConnectionStyle.CQL_PREPARED, SettingsMode.get(new HashMap<>()).style);
    }

    @Test
    void unpreparedSelectsPlainCql() {
        assertEquals(ConnectionStyle.CQL, parse("cql3", "native", "unprepared").style);
    }

    @ParameterizedTest
    @ValueSource(strings = {"protocolVersion=2", "protocolVersion=6", "bogus"})
    void rejectsAnUnsupportedValue(String param) {
        assertThrows(IllegalArgumentException.class, () -> parse("cql3", param));
    }

    @Test
    void nativeSelectsDriver3AndIsTheDefault() {
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, parse("cql3", "native").api);
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, parse("cql3").api);
        assertEquals(ConnectionAPI.JAVA_DRIVER_NATIVE, SettingsMode.get(new HashMap<>()).api);
    }

    @Test
    void fourXSelectsDriver4() {
        assertEquals(ConnectionAPI.JAVA_DRIVER4_NATIVE, parse("cql3", "4x").api);
    }

    @Test
    void idempotenceAndSpeculationAreOffByDefault() {
        SettingsMode mode = parse("cql3", "native");

        assertFalse(mode.idempotent);
        assertNull(mode.speculativeExecutions);
        assertNull(mode.speculativeDelay);
        assertEquals(List.of("  Idempotent Statements: false", "  Speculative Execution: none"), lastLines(mode, 2));
    }

    @ParameterizedTest
    @CsvSource({"500, 10", "10, 10", "4, 4"})
    void theSpeculativeTimerTicksAtMostEveryTenMillisecondsAndNeverSlowerThanTheDelay(int delay, int tick) {
        SettingsMode mode =
                parse("cql3", "native", "idempotent", "speculativeExecutions=2", "speculativeDelay=" + delay);

        assertEquals(tick, mode.speculativeTimerTick());
    }

    @Test
    void withoutSpeculationTheTimerTickIsTheDriverDefault() {
        assertNull(parse("cql3", "native", "idempotent").speculativeTimerTick());
    }

    @Test
    void idempotentAloneTurnsOnIdempotenceWithoutSpeculation() {
        SettingsMode mode = parse("cql3", "4x", "idempotent");

        assertTrue(mode.idempotent);
        assertNull(mode.speculativeExecutions);
    }

    @Test
    void speculationTakesTheTotalExecutionsAndTheDelay() {
        SettingsMode mode = parse("cql3", "native", "idempotent", "speculativeExecutions=3", "speculativeDelay=500");

        assertEquals(3, mode.speculativeExecutions);
        assertEquals(500, mode.speculativeDelay);
        assertEquals(
                List.of("  Idempotent Statements: true", "  Speculative Execution: 3 executions, 500 ms delay"),
                lastLines(mode, 2));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "speculativeExecutions=3 speculativeDelay=500|speculativeExecutions= and speculativeDelay= need"
                        + " idempotent",
                "idempotent speculativeDelay=500|speculativeDelay= needs speculativeExecutions=",
                "idempotent speculativeExecutions=3|speculativeExecutions= needs speculativeDelay=",
                "idempotent speculativeExecutions=1 speculativeDelay=500"
                        + "|speculativeExecutions= must be 2 or more, it counts the first execution",
                "idempotent speculativeExecutions=2 speculativeDelay=0"
                        + "|speculativeDelay= must be 1 or more, 0 sends every execution at once",
            })
    void rejectsAnIncompleteSpeculationSetting(String params, String message) {
        List<String> args = new ArrayList<>(List.of("cql3", "native"));
        args.addAll(List.of(params.split(" ")));

        InvalidSettingsException e =
                assertThrows(InvalidSettingsException.class, () -> parse(args.toArray(String[]::new)));
        assertEquals(message, e.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
        "speculativeExecutions=99999999999, speculativeExecutions=",
        "speculativeDelay=99999999999, speculativeDelay="
    })
    void rejectsASpeculationValueOutsideTheIntegerRange(String param, String name) {
        InvalidSettingsException e =
                assertThrows(InvalidSettingsException.class, () -> parse("cql3", "native", "idempotent", param));
        assertEquals("Invalid value for " + name + " 99999999999", e.getMessage());
    }

    private static List<String> lastLines(SettingsMode mode, int count) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        mode.printSettings(new MultiResultLogger(new PrintStream(bytes, true, StandardCharsets.UTF_8)));
        List<String> lines = bytes.toString(StandardCharsets.UTF_8).lines().toList();
        return lines.subList(lines.size() - count, lines.size());
    }

    @ParameterizedTest
    @CsvSource({"native, JAVA_DRIVER_NATIVE", "4x, JAVA_DRIVER4_NATIVE"})
    void printsTheApiFirst(String driver, String api) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        parse("cql3", driver)
                .printSettings(new org.apache.cassandra.stress.util.MultiResultLogger(
                        new java.io.PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(
                "  API: " + api,
                bytes.toString(java.nio.charset.StandardCharsets.UTF_8)
                        .lines()
                        .findFirst()
                        .orElseThrow());
    }
}
