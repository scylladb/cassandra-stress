// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.cassandra.stress.util.ResultLogger;

public class SettingsErrors {

    public final boolean ignore;
    public final boolean failFast;
    public final int tries;
    public final boolean skipReadValidation;
    public final boolean skipUnsupportedColumns;

    private enum DelayPolicy {
        CONSTANT,
        LINEAR,
        EXPONENTIAL
    }

    private final DelayPolicy delayPolicy;
    private final long minDelayMs;
    private final long maxDelayMs;

    public SettingsErrors(Options options) {
        ignore = options.ignore.setByUser();
        failFast = options.failFast.setByUser();
        this.tries = Math.max(1, Integer.parseInt(options.retries.value()) + 1);
        skipReadValidation = options.skipReadValidation.setByUser();
        skipUnsupportedColumns = options.skipUnsupportedColumns.setByUser();
        delayPolicy = DelayPolicy.valueOf(options.delayPolicy.value().toUpperCase(Locale.ROOT));
        minDelayMs = Long.parseLong(options.minDelayMs.value());
        maxDelayMs = Long.parseLong(options.maxDelayMs.value());
    }

    public Duration nextDelay(int numTries) {
        assert numTries >= 0;
        assert numTries < tries;
        long delay = switch (delayPolicy) {
            case CONSTANT -> minDelayMs;
            case LINEAR -> (long) (minDelayMs + (maxDelayMs - minDelayMs) * (float) numTries / tries);
            case EXPONENTIAL -> minDelayMs * (1L << numTries);
        };
        double jitterRatio = ThreadLocalRandom.current().nextDouble(0.75, 1.25);
        delay = (long) (delay * jitterRatio);
        delay = Math.max(delay, minDelayMs);
        delay = Math.min(delay, maxDelayMs);
        return Duration.ofMillis(delay);
    }

    public static final class Options extends GroupedOptions {
        final OptionSimple retries = new OptionSimple(
                "retries=", "[0-9]+", "9", "Number of tries to perform for each operation before failing", false);
        final OptionSimple ignore = new OptionSimple("ignore", "", null, "Do not fail on errors", false);
        final OptionSimple failFast = new OptionSimple(
                "fail-fast", "", null, "Fail on first thread failure when running for set <duration>", false);
        final OptionSimple skipReadValidation =
                new OptionSimple("skip-read-validation", "", null, "Skip read validation and message output", false);
        final OptionSimple skipUnsupportedColumns = new OptionSimple(
                "skip-unsupported-columns",
                "",
                null,
                "Skip unsupported columns, such as maps and embedded collections, when generating data for a user"
                        + " profile.",
                false);

        final OptionSimple delayPolicy = new OptionSimple(
                "delay-policy=",
                "constant|linear|exponential",
                "constant",
                "Delay before next retry: constant, waits a constant time; linear, increases linearly, exponential,"
                        + " double the delay every time",
                false);
        final OptionSimple minDelayMs = new OptionSimple(
                "min-delay-ms=",
                "[0-9]+",
                "0",
                "Minimum delay in milliseconds, please use a non-zero value with exponential",
                false);
        final OptionSimple maxDelayMs =
                new OptionSimple("max-delay-ms=", "[0-9]+", "20000", "Maximum delay in milliseconds", false);

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(
                    retries,
                    ignore,
                    failFast,
                    skipReadValidation,
                    skipUnsupportedColumns,
                    delayPolicy,
                    minDelayMs,
                    maxDelayMs);
        }

        @Override
        public boolean happy() {
            if (!super.happy()) {
                return false;
            }
            long minDelay = Long.parseLong(minDelayMs.value());
            long maxDelay = Long.parseLong(maxDelayMs.value());
            if (minDelay > maxDelay) {
                return false;
            }
            if (DelayPolicy.valueOf(delayPolicy.value().toUpperCase(Locale.ROOT)) == DelayPolicy.EXPONENTIAL) {
                return minDelay > 0;
            }
            return true;
        }
    }

    public void printSettings(ResultLogger out) {
        out.printf("  Ignore: %b%n", ignore);
        out.printf("  Fail fast setting: %b%n", failFast);
        out.printf("  Tries: %d%n", tries);
        if (delayPolicy == DelayPolicy.CONSTANT && minDelayMs == 0) {
            return;
        }
        out.printf("  RetryPolicy: %s%n", delayPolicy.toString().toLowerCase(Locale.ROOT));
        out.printf("  Minimum Delay: %,d %s%n", minDelayMs, TimeUnit.MILLISECONDS.toString());
        if (delayPolicy != DelayPolicy.CONSTANT) {
            out.printf("  Maximum Delay: %,d %s%n", maxDelayMs, TimeUnit.MILLISECONDS.toString());
        }
    }

    public static SettingsErrors get(Map<String, String[]> clArgs) {
        String[] params = clArgs.remove("-errors");
        if (params == null) return new SettingsErrors(new Options());

        GroupedOptions options = GroupedOptions.select(params, new Options());
        if (options == null) {
            printHelp();
            System.out.println("Invalid -errors options provided, see output for valid options");
            System.exit(1);
        }
        return new SettingsErrors((Options) options);
    }

    public static void printHelp() {
        GroupedOptions.printOptions(System.out, "-errors", new Options());
    }

    public static Runnable helpPrinter() {
        return () -> printHelp();
    }
}
