// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

class OptionCompaction extends OptionMulti {

    private final OptionSimple strategy =
            new OptionSimple("strategy=", new StrategyAdapter(), null, "The compaction strategy to use", false);

    OptionCompaction() {
        super("compaction", "Define the compaction strategy and any parameters", true);
    }

    public String getStrategy() {
        return strategy.value();
    }

    public Map<String, String> getOptions() {
        return extraOptions();
    }

    @Override
    protected List<? extends Option> options() {
        return List.of(strategy);
    }

    @Override
    public boolean happy() {
        return true;
    }

    private static final class StrategyAdapter implements Function<String, String> {

        @Override
        public String apply(String name) {
            return CompactionStrategy.validate(name);
        }
    }
}
