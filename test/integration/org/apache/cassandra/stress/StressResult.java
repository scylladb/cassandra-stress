package org.apache.cassandra.stress;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record StressResult(int exitCode, String output) {
    private static final Pattern TOTAL_ERRORS = Pattern.compile("^Total errors\\s*:\\s*([0-9,]+)", Pattern.MULTILINE);
    private static final Pattern MAX_LATENCY = Pattern.compile("^Latency max\\s*:\\s*([0-9.,]+) ms", Pattern.MULTILINE);
    private static final Pattern TOTAL_PARTITIONS =
            Pattern.compile("^Total partitions\\s*:\\s*([0-9,]+)", Pattern.MULTILINE);

    boolean succeeded() {
        return exitCode == 0 && output.lines().anyMatch("END"::equals);
    }

    Optional<Long> totalErrors() {
        return find(TOTAL_ERRORS).map(this::parseLong);
    }

    Optional<Long> totalPartitions() {
        return find(TOTAL_PARTITIONS).map(this::parseLong);
    }

    Optional<Double> maxLatencyMillis() {
        return find(MAX_LATENCY).map(this::parseDouble);
    }

    private Optional<String> find(Pattern pattern) {
        Matcher matcher = pattern.matcher(output);
        return matcher.find() ? Optional.of(matcher.group(1).replace(",", "")) : Optional.empty();
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw unparsable(value, e);
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw unparsable(value, e);
        }
    }

    private IllegalStateException unparsable(String value, NumberFormatException cause) {
        return new IllegalStateException("Cannot parse \"" + value + "\" in\n" + this, cause);
    }

    @Override
    public String toString() {
        return "exit code " + exitCode + "\n" + output;
    }
}
