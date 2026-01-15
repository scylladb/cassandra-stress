package com.scylladb.utils;

import com.scylladb.io.DiskOptimizationStrategy;
import com.scylladb.io.SpinningDiskOptimizationStrategy;

public class DatabaseDescriptor {
    private static DiskOptimizationStrategy diskOptimizationStrategy;

    private static boolean clientInitialized;
    private static boolean toolInitialized;
    private static boolean daemonInitialized;

    /**
     * Initializes this class as a client, which means that just an empty configuration will
     * be used.
     *
     * @param failIfDaemonOrTool if {@code true} and a call to has been performed before, an
     *                           {@link AssertionError} will be thrown.
     */
    public static void clientInitialization(boolean failIfDaemonOrTool) {
        if (!failIfDaemonOrTool && (daemonInitialized || toolInitialized)) {
            return;
        } else if (daemonInitialized) {
            throw new AssertionError("daemonInitialization() already called");
        } else if (toolInitialized) {
            throw new AssertionError("toolInitialization() already called");
        }

        if (clientInitialized)
            return;
        clientInitialized = true;

        diskOptimizationStrategy = new SpinningDiskOptimizationStrategy();
    }
}
