// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.operations.OpDistributionFactory;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.ResultLogger;
import org.apache.cassandra.stress.util.Sleep;

public abstract class SettingsCommand {

    public enum TruncateWhen {
        NEVER,
        ONCE,
        ALWAYS
    }

    public final Command type;
    public final long count;
    public final long duration;
    public final TimeUnit durationUnits;
    public final boolean noWarmup;
    public final TruncateWhen truncate;
    public final ConsistencyLevel consistencyLevel;
    public final ConsistencyLevel serialConsistencyLevel;
    public final double targetUncertainty;
    public final int minimumUncertaintyMeasurements;
    public final int maximumUncertaintyMeasurements;

    public abstract OpDistributionFactory getFactory(StressSettings settings);

    public SettingsCommand(Command type, GroupedOptions options) {
        this(
                type,
                (Options) options,
                options instanceof Count count ? count : null,
                options instanceof Duration duration ? duration : null,
                options instanceof Uncertainty uncertainty ? uncertainty : null);
    }

    public SettingsCommand(Command type, Options options, Count count, Duration duration, Uncertainty uncertainty) {
        this.type = type;
        this.consistencyLevel =
                ConsistencyLevel.valueOf(options.consistencyLevel.value().toUpperCase(Locale.ROOT));
        this.serialConsistencyLevel =
                ConsistencyLevel.valueOf(options.serialConsistencyLevel.value().toUpperCase(Locale.ROOT));
        this.noWarmup = options.noWarmup.setByUser();
        this.truncate = TruncateWhen.valueOf(options.truncate.value().toUpperCase(Locale.ROOT));

        if (count != null) {
            this.count = OptionDistribution.parseLong(count.count.value());
            this.duration = 0;
            this.durationUnits = null;
            this.targetUncertainty = -1;
            this.minimumUncertaintyMeasurements = -1;
            this.maximumUncertaintyMeasurements = -1;
        } else if (duration != null) {
            this.count = -1;
            this.duration = Long.parseLong(duration.duration
                    .value()
                    .substring(0, duration.duration.value().length() - 1));
            this.durationUnits = switch (duration.duration
                    .value()
                    .toLowerCase(Locale.ROOT)
                    .charAt(duration.duration.value().length() - 1)) {
                case 's' -> TimeUnit.SECONDS;
                case 'm' -> TimeUnit.MINUTES;
                case 'h' -> TimeUnit.HOURS;
                default -> throw new IllegalStateException();
            };
            this.targetUncertainty = -1;
            this.minimumUncertaintyMeasurements = -1;
            this.maximumUncertaintyMeasurements = -1;
        } else {
            this.count = -1;
            this.duration = 0;
            this.durationUnits = null;
            this.targetUncertainty = Double.parseDouble(uncertainty.uncertainty.value());
            this.minimumUncertaintyMeasurements = Integer.parseInt(uncertainty.minMeasurements.value());
            this.maximumUncertaintyMeasurements = Integer.parseInt(uncertainty.maxMeasurements.value());
        }
    }

    abstract static class Options extends GroupedOptions {
        final OptionSimple noWarmup = new OptionSimple("no-warmup", "", null, "Do not warmup the process", false);
        final OptionSimple truncate = new OptionSimple(
                "truncate=",
                "never|once|always",
                "never",
                "Truncate the table: never, before performing any work, or before each iteration",
                false);
        final OptionSimple consistencyLevel = new OptionSimple(
                "cl=",
                "ONE|QUORUM|LOCAL_QUORUM|EACH_QUORUM|ALL|ANY|TWO|THREE|LOCAL_ONE|SERIAL|LOCAL_SERIAL",
                "LOCAL_ONE",
                "Consistency level to use",
                false);
        final OptionSimple serialConsistencyLevel = new OptionSimple(
                "serial-cl=", "SERIAL|LOCAL_SERIAL", "SERIAL", "Serial consistency level to use", false);
    }

    static class Count extends Options {
        final OptionSimple count =
                new OptionSimple("n=", "[0-9]+[bmk]?", null, "Number of operations to perform", true);

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(count, noWarmup, truncate, consistencyLevel, serialConsistencyLevel);
        }
    }

    static class Duration extends Options {
        final OptionSimple duration = new OptionSimple(
                "duration=", "[0-9]+[smh]", null, "Time to run in (in seconds, minutes or hours)", true);

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(duration, noWarmup, truncate, consistencyLevel, serialConsistencyLevel);
        }
    }

    static class Uncertainty extends Options {
        final OptionSimple uncertainty = new OptionSimple(
                "err<", "0\\.[0-9]+", "0.02", "Run until the standard error of the mean is below this fraction", false);
        final OptionSimple minMeasurements = new OptionSimple(
                "n>",
                "[0-9]+",
                "30",
                "Run at least this many iterations before accepting uncertainty convergence",
                false);
        final OptionSimple maxMeasurements = new OptionSimple(
                "n<",
                "[0-9]+",
                "200",
                "Run at most this many iterations before accepting uncertainty convergence",
                false);

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(
                    uncertainty,
                    minMeasurements,
                    maxMeasurements,
                    noWarmup,
                    truncate,
                    consistencyLevel,
                    serialConsistencyLevel);
        }
    }

    public abstract void truncateTables(StressSettings settings);

    protected void truncateTables(StressSettings settings, String ks, String... tables) {
        StressClient client = settings.getClient(false);
        assert settings.command.truncate != SettingsCommand.TruncateWhen.NEVER;
        for (String table : tables) {
            String cql = String.format("TRUNCATE %s.%s", ks, table);
            client.execute(cql, org.apache.cassandra.stress.util.ConsistencyLevel.ONE);
        }
        settings.output()
                .println(String.format(
                        "Truncated %s.%s. Sleeping %ss for propagation.",
                        ks, Arrays.toString(tables), settings.node.nodes.size()));
        Sleep.uninterruptibly(settings.node.nodes.size(), TimeUnit.SECONDS);
    }

    public void printSettings(ResultLogger out) {
        out.printf("  Type: %s%n", type.toString().toLowerCase(Locale.ROOT));
        out.printf("  Count: %,d%n", count);
        if (durationUnits != null) {
            out.printf("  Duration: %,d %s%n", duration, durationUnits.toString());
        }
        out.printf("  No Warmup: %s%n", noWarmup);
        out.printf("  Consistency Level: %s%n", consistencyLevel.toString());
        out.printf("  Serial Consistency Level: %s%n", serialConsistencyLevel.toString());
        if (targetUncertainty != -1) {
            out.printf("  Target Uncertainty: %.3f%n", targetUncertainty);
            out.printf("  Minimum Uncertainty Measurements: %,d%n", minimumUncertaintyMeasurements);
            out.printf("  Maximum Uncertainty Measurements: %,d%n", maximumUncertaintyMeasurements);
        } else {
            out.printf("  Target Uncertainty: not applicable%n");
        }
    }

    static SettingsCommand get(Map<String, String[]> clArgs) {
        for (Command cmd : Command.values()) {
            if (cmd.category == null) continue;

            for (String name : cmd.names) {
                final String[] params = clArgs.remove(name);
                if (params == null) continue;

                return switch (cmd.category) {
                    case BASIC -> SettingsCommandPreDefined.build(cmd, params);
                    case MIXED -> SettingsCommandPreDefinedMixed.build(params);
                    case USER -> SettingsCommandUser.build(params);
                };
            }
        }
        return null;
    }

    static void printHelp(Command type) {
        printHelp(type.toString().toLowerCase(Locale.ROOT));
    }

    static void printHelp(String type) {
        GroupedOptions.printOptions(
                System.out, type.toLowerCase(Locale.ROOT), new Uncertainty(), new Count(), new Duration());
    }

    static Runnable helpPrinter(final Command type) {
        return () -> printHelp(type);
    }
}
