package org.apache.cassandra.stress;

import java.util.Optional;
import java.util.function.Function;
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
        return find(TOTAL_ERRORS, Long::valueOf);
    }

    Optional<Long> totalPartitions() {
        return find(TOTAL_PARTITIONS, Long::valueOf);
    }

    Optional<Double> maxLatencyMillis() {
        return find(MAX_LATENCY, Double::valueOf);
    }

    private <T> Optional<T> find(Pattern pattern, Function<String, T> parse) {
        Matcher matcher = pattern.matcher(output);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse.apply(matcher.group(1).replace(",", "")));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Cannot parse \"" + matcher.group(1) + "\" in\n" + this, e);
        }
    }

    @Override
    public String toString() {
        return "exit code " + exitCode + "\n" + output;
    }
}
