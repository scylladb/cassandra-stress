// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.cassandra.stress.Operation;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.operations.OpDistributionFactory;
import org.apache.cassandra.stress.operations.SampledOpDistributionFactory;
import org.apache.cassandra.stress.operations.predefined.PredefinedOperation;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.util.ResultLogger;

public class SettingsCommandPreDefinedMixed extends SettingsCommandPreDefined {

    private final Map<Command, Double> ratios;
    private final DistributionFactory clustering;
    private final Options mixedOptions;

    public SettingsCommandPreDefinedMixed(Options options) {
        super(Command.MIXED, options);

        clustering = options.clustering.get();
        ratios = options.probabilities.ratios();
        this.mixedOptions = options;
        if (ratios.isEmpty())
            throw new IllegalArgumentException("Must specify at least one command with a non-zero ratio");
    }

    @Override
    public OpDistributionFactory getFactory(final StressSettings settings) {
        final SeedManager seeds = new SeedManager(settings);
        return new SampledOpDistributionFactory<Command>(ratios, clustering) {
            @Override
            protected List<? extends Operation> get(Timer timer, Command key, boolean isWarmup) {
                return Collections.singletonList(PredefinedOperation.operation(
                        key, timer, SettingsCommandPreDefinedMixed.this.newGenerator(settings), seeds, settings, add));
            }
        };
    }

    static class Options extends SettingsCommandPreDefined.Options {
        static List<OptionEnumProbabilities.Opt<Command>> probabilityOptions = new ArrayList<>();

        static {
            for (Command command : Command.values()) {
                if (command.category == null || command == Command.MIXED) continue;
                String defaultValue = switch (command) {
                    case READ, WRITE -> "1";
                    default -> null;
                };
                probabilityOptions.add(new OptionEnumProbabilities.Opt<>(command, defaultValue));
            }
        }

        protected Options(SettingsCommand.Options parent) {
            super(parent);
        }

        final OptionDistribution clustering = new OptionDistribution(
                "clustering=", "GAUSSIAN(1..10)", "Distribution clustering runs of operations of the same kind");
        final OptionEnumProbabilities probabilities = new OptionEnumProbabilities<>(
                probabilityOptions,
                "ratio",
                "Specify the ratios for operations to perform; e.g. (read=2,write=1) will perform 2 reads for each"
                        + " write");

        @Override
        public List<? extends Option> options() {
            return merge(Arrays.asList(clustering, probabilities), super.options());
        }
    }

    @Override
    public void printSettings(ResultLogger out) {
        super.printSettings(out);
        out.printf("  Command Ratios: %s%n", ratios);
        out.printf("  Command Clustering Distribution: %s%n", mixedOptions.clustering.getOptionAsString());
    }

    public static SettingsCommandPreDefinedMixed build(String[] params) {
        GroupedOptions options = GroupedOptions.select(
                params,
                new Options(new SettingsCommand.Uncertainty()),
                new Options(new SettingsCommand.Count()),
                new Options(new SettingsCommand.Duration()));
        if (options == null) {
            throw new InvalidSettingsException(
                    "Invalid MIXED options provided, see output for valid options",
                    SettingsCommandPreDefinedMixed::printHelp);
        }
        return new SettingsCommandPreDefinedMixed((Options) options);
    }

    public static void printHelp() {
        GroupedOptions.printOptions(
                System.out,
                "mixed",
                new Options(new SettingsCommand.Uncertainty()),
                new Options(new SettingsCommand.Count()),
                new Options(new SettingsCommand.Duration()));
    }

    public static Runnable helpPrinter() {
        return () -> printHelp();
    }
}
