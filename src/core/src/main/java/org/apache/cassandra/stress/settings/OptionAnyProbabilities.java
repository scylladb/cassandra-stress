// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OptionAnyProbabilities extends OptionMulti {
    public OptionAnyProbabilities(String name, String description) {
        super(name, description, false);
    }

    final CollectRatios ratios = new CollectRatios();

    private static final class CollectRatios extends Option {
        Map<String, Double> options = new LinkedHashMap<>();

        @Override
        boolean accept(String param) {
            String[] args = param.split("=");
            if (args.length == 2 && args[1].length() > 0 && args[0].length() > 0) {
                if (options.put(args[0], Double.valueOf(args[1])) != null) {
                    throw new IllegalArgumentException(args[0] + " set twice");
                }
                return true;
            }
            return false;
        }

        @Override
        boolean happy() {
            return !options.isEmpty();
        }

        @Override
        String shortDisplay() {
            return null;
        }

        @Override
        public String getOptionAsString() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Double> entry : options.entrySet()) {
                sb.append(entry.getKey()).append('=').append(entry.getValue()).append(',');
            }
            return sb.toString();
        }

        @Override
        String longDisplay() {
            return null;
        }

        @Override
        List<String> multiLineDisplay() {
            return List.of();
        }

        @Override
        boolean setByUser() {
            return !options.isEmpty();
        }

        @Override
        boolean present() {
            return setByUser();
        }
    }

    @Override
    public List<? extends Option> options() {
        return List.of(ratios);
    }

    Map<String, Double> ratios() {
        return ratios.options;
    }

    @Override
    public String getOptionAsString() {
        StringBuilder sb = new StringBuilder(super.getOptionAsString());
        sb.append(" [Ratios: ");
        sb.append(ratios.getOptionAsString());
        sb.append("];");
        return sb.toString();
    }
}
