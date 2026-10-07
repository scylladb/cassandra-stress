// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.Locale;

public enum LoadBalanceType {
    ROUND_ROBIN,
    DC_AWARE,
    RACK_AWARE;

    public static LoadBalanceType of(SettingsNode node) {
        if (node.loadBalance != null) return node.loadBalance;
        return node.rack != null ? RACK_AWARE : DC_AWARE;
    }

    public static LoadBalanceType fromString(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "rr", "roundrobin", "round-robin" -> ROUND_ROBIN;
            case "dc", "dc-aware" -> DC_AWARE;
            case "rack", "rack-aware" -> RACK_AWARE;
            default -> throw new IllegalArgumentException("Unknown load balance strategy: " + value);
        };
    }

    @Override
    public String toString() {
        return switch (this) {
            case ROUND_ROBIN -> "round-robin";
            case DC_AWARE -> "dc-aware";
            case RACK_AWARE -> "rack-aware";
        };
    }
}
