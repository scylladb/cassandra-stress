package org.apache.cassandra.stress;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record StressResult(int exitCode, String output) {
    private static final Pattern TOTAL_ERRORS = Pattern.compile("^Total errors\\s*:\\s*([0-9,]+)", Pattern.MULTILINE);
    private static final Pattern TOTAL_PARTITIONS =
            Pattern.compile("^Total partitions\\s*:\\s*([0-9,]+)", Pattern.MULTILINE);

    boolean succeeded() {
        return exitCode == 0 && output.lines().anyMatch("END"::equals);
    }

    Optional<Long> totalErrors() {
        return find(TOTAL_ERRORS);
    }

    Optional<Long> totalPartitions() {
        return find(TOTAL_PARTITIONS);
    }

    private Optional<Long> find(Pattern pattern) {
        Matcher matcher = pattern.matcher(output);
        if (!matcher.find()) return Optional.empty();
        return Optional.of(Long.parseLong(matcher.group(1).replace(",", "")));
    }

    @Override
    public String toString() {
        return "exit code " + exitCode + "\n" + output;
    }
}
