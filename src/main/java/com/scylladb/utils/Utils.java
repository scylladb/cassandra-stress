package com.scylladb.utils;

public final class Utils {
    private static final String OPERATING_SYSTEM = System.getProperty("os.name").toLowerCase();
    public static final boolean isWindows = OPERATING_SYSTEM.contains("windows");
    public static final boolean isLinux = OPERATING_SYSTEM.contains("linux");

    public static String prettyPrintMemory(long size, boolean includeSpace) {
        if (size >= 1 << 30)
            return String.format("%.3f%sGiB", size / (double) (1 << 30), includeSpace ? " " : "");
        if (size >= 1 << 20)
            return String.format("%.3f%sMiB", size / (double) (1 << 20), includeSpace ? " " : "");
        return String.format("%.3f%sKiB", size / (double) (1 << 10), includeSpace ? " " : "");
    }
}
