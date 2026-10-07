package org.apache.cassandra.stress.settings;

public enum CompactionStrategy {
    SizeTieredCompactionStrategy,
    LeveledCompactionStrategy,
    TimeWindowCompactionStrategy,
    DateTieredCompactionStrategy,
    IncrementalCompactionStrategy;

    private static final String PACKAGE = "org.apache.cassandra.db.compaction.";

    public static String validate(String name) {
        String shortName = name.startsWith(PACKAGE) ? name.substring(PACKAGE.length()) : name;
        for (CompactionStrategy strategy : values()) {
            if (strategy.name().equals(shortName)) {
                return name;
            }
        }
        throw new IllegalArgumentException("Invalid compaction strategy: " + name);
    }
}
