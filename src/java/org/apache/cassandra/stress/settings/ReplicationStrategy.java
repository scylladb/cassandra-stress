package org.apache.cassandra.stress.settings;

public enum ReplicationStrategy {
    NetworkTopologyStrategy,
    EverywhereStrategy;

    private static final String PACKAGE = "org.apache.cassandra.locator.";

    public static String validate(String name) {
        String shortName = name.startsWith(PACKAGE) ? name.substring(PACKAGE.length()) : name;
        for (ReplicationStrategy strategy : values()) {
            if (strategy.name().equals(shortName)) return PACKAGE + strategy.name();
        }
        throw new IllegalArgumentException("Invalid replication strategy: " + name);
    }
}
