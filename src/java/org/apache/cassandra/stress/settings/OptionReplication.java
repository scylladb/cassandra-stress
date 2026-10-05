// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class OptionReplication extends OptionMulti {
    private static final String DEFAULT_STRATEGY = "org.apache.cassandra.locator.NetworkTopologyStrategy";

    private final OptionSimple strategy = new OptionSimple(
            "strategy=", new StrategyAdapter(), DEFAULT_STRATEGY, "The replication strategy to use", false);
    private final OptionSimple factor = new OptionSimple("factor=", "[0-9]+", "1", "The number of replicas", false);

    OptionReplication() {
        super("replication", "Define the replication strategy and any parameters", true);
    }

    public String getStrategy() {
        return strategy.value();
    }

    public Map<String, String> getOptions() {
        Map<String, String> options = extraOptions();
        if (!options.containsKey("replication_factor")
                && (DEFAULT_STRATEGY.equals(strategy.value()) || factor.setByUser()))
            options.put("replication_factor", factor.value());
        return options;
    }

    @Override
    protected List<? extends Option> options() {
        return Arrays.asList(strategy, factor);
    }

    @Override
    public boolean happy() {
        return true;
    }

    private static final class StrategyAdapter implements Function<String, String> {
        @Override
        public String apply(String name) {
            return ReplicationStrategy.validate(name);
        }
    }
}
