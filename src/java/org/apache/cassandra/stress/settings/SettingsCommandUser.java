// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.cassandra.stress.Operation;
import org.apache.cassandra.stress.StressProfile;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.generate.TokenRangeIterator;
import org.apache.cassandra.stress.operations.OpDistributionFactory;
import org.apache.cassandra.stress.operations.SampledOpDistributionFactory;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.util.ResultLogger;

public class SettingsCommandUser extends SettingsCommand {

    private final Map<String, Double> ratios;
    private final DistributionFactory clustering;
    public final Map<String, StressProfile> profiles;
    private final Options options;
    private String defaultProfileName;
    private static final Pattern EXTRACT_SPEC_CMD = Pattern.compile("(.+)\\.(.+)");

    public SettingsCommandUser(Options options) {
        super(Command.USER, options.parent);

        this.options = options;
        clustering = options.clustering.get();
        ratios = options.ops.ratios();
        defaultProfileName = null;

        String yamlPath = options.profile.value();
        profiles = new LinkedHashMap<>();

        String[] yamlPaths = yamlPath.split(",");
        for (String curYamlPath : yamlPaths) {
            File yamlFile = new File(curYamlPath);
            StressProfile profile = StressProfile.load(yamlFile.exists() ? yamlFile.toURI() : URI.create(curYamlPath));
            String specName = profile.specName;
            if (defaultProfileName == null) {
                defaultProfileName = specName;
            }

            if (profiles.containsKey(specName)) {
                throw new IllegalArgumentException(
                        "Must only specify a singe YAML file per table (including keyspace qualifier).");
            }

            profiles.put(specName, profile);
        }

        if (ratios.isEmpty()) {
            throw new IllegalArgumentException("Must specify at least one command with a non-zero ratio");
        }
    }

    public boolean hasInsertOnly() {
        return ratios.size() == 1 && ratios.containsKey("insert");
    }

    @Override
    public OpDistributionFactory getFactory(final StressSettings settings) {
        final SeedManager seeds = new SeedManager(settings);

        final Map<String, TokenRangeIterator> tokenRangeIterators = new LinkedHashMap<>();
        profiles.forEach((k, v) -> tokenRangeIterators.put(
                k,
                (v.tokenRangeQueries.isEmpty()
                        ? null
                        : new TokenRangeIterator(settings, v.maybeLoadTokenRanges(settings)))));

        return new SampledOpDistributionFactory<String>(ratios, clustering) {
            @Override
            protected List<? extends Operation> get(Timer timer, String key, boolean isWarmup) {
                Matcher m = EXTRACT_SPEC_CMD.matcher(key);
                final String profile_name;
                final String sub_key;
                if (m.matches()) {
                    profile_name = m.group(1);
                    sub_key = m.group(2);
                } else {
                    profile_name = defaultProfileName;
                    sub_key = key;
                }

                if (!profiles.containsKey(profile_name)) {
                    throw new IllegalArgumentException(String.format(
                            Locale.ROOT, "Op name %s contains an invalid profile specname: %s", key, profile_name));
                }
                StressProfile profile = profiles.get(profile_name);
                TokenRangeIterator tokenRangeIterator = tokenRangeIterators.get(profile_name);
                PartitionGenerator generator = profile.newGenerator(settings);
                if ("insert".equalsIgnoreCase(sub_key)) {
                    return Collections.singletonList(profile.getInsert(timer, generator, seeds, settings));
                }
                if ("validate".equalsIgnoreCase(sub_key)) {
                    return profile.getValidate(timer, generator, seeds, settings);
                }

                if (profile.tokenRangeQueries.containsKey(sub_key)) {
                    return Collections.singletonList(
                            profile.getBulkReadQueries(sub_key, timer, settings, tokenRangeIterator, isWarmup));
                }

                return Collections.singletonList(profile.getQuery(sub_key, timer, generator, seeds, settings));
            }
        };
    }

    @Override
    public void truncateTables(StressSettings settings) {
        profiles.forEach((k, v) -> v.truncateTable(settings));
    }

    static final class Options extends GroupedOptions {
        final SettingsCommand.Options parent;

        Options(SettingsCommand.Options parent) {
            this.parent = parent;
        }

        final OptionDistribution clustering = new OptionDistribution(
                "clustering=", "gaussian(1..10)", "Distribution clustering runs of operations of the same kind");
        final OptionSimple profile = new OptionSimple(
                "profile=",
                ".*",
                null,
                "Specify the path to a yaml cql3 profile. Multiple comma separated files can be added.",
                true);
        final OptionAnyProbabilities ops = new OptionAnyProbabilities(
                "ops",
                "Specify the ratios for inserts/queries to perform; e.g. ops(insert=2,<query1>=1) will perform 2"
                        + " inserts for each query1. When using multiple files, specify as keyspace.table.op.");

        @Override
        public List<? extends Option> options() {
            return merge(Arrays.asList(ops, profile, clustering), parent.options());
        }
    }

    @Override
    public void printSettings(ResultLogger out) {
        super.printSettings(out);
        out.printf("  Command Ratios: %s%n", ratios);
        out.printf("  Command Clustering Distribution: %s%n", options.clustering.getOptionAsString());
        out.printf("  Profile File(s): %s%n", options.profile.value());
    }

    public static SettingsCommandUser build(String[] params) {
        GroupedOptions options = GroupedOptions.select(
                params, new Options(new Uncertainty()), new Options(new Duration()), new Options(new Count()));
        if (options == null) {
            throw new InvalidSettingsException(
                    "Invalid USER options provided, see output for valid options", SettingsCommandUser::printHelp);
        }
        return new SettingsCommandUser((Options) options);
    }

    public static void printHelp() {
        GroupedOptions.printOptions(
                System.out,
                "user",
                new Options(new Uncertainty()),
                new Options(new Count()),
                new Options(new Duration()));
    }

    public static Runnable helpPrinter() {
        return () -> printHelp();
    }
}
