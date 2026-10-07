// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.apache.cassandra.stress.driver.OverloadedException;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.SettingsLog;
import org.apache.cassandra.stress.settings.StressSettings;

public abstract class Operation {
    public final StressSettings settings;
    private final Timer timer;

    public Operation(Timer timer, StressSettings settings) {
        this.timer = timer;
        this.settings = settings;
    }

    public interface RunOp {
        boolean run() throws Exception;

        int partitionCount();

        int rowCount();

        default String validationErrorMessage() {
            return null;
        }
    }

    public abstract int ready(WorkManager permits);

    protected static String hexPreview(ByteBuffer bb, int maxBytes) {
        if (bb == null) return "null";
        byte[] head = new byte[Math.min(bb.remaining(), maxBytes)];
        bb.duplicate().get(head);
        return "0x" + HexFormat.of().formatHex(head) + (bb.remaining() > maxBytes ? "..." : "");
    }

    public boolean isWrite() {
        return false;
    }

    public abstract void run(StressClient client) throws IOException;

    @SuppressWarnings("EmptyCatch")
    public final void timeWithRetry(RunOp run) throws IOException {
        timer.start();

        boolean success = false;
        String exceptionMessage = null;

        int tries = 0;
        for (; tries < settings.errors.tries; tries++) {
            try {
                success = run.run();
                break;
            } catch (NoSuchElementException e) {
                throw e;
            } catch (OverloadedException e) {
                exceptionMessage = getExceptionMessage(e);
                if (tries + 1 >= settings.errors.tries) continue;
                try {
                    if (settings.log.level.compareTo(SettingsLog.Level.MINIMAL) > 0) {
                        System.err.println(String.format(
                                Locale.ROOT, "Server is overloaded, retry %d/%d times", tries, settings.errors.tries));
                    }
                    Thread.sleep(settings.errors.nextDelay(tries).toMillis());
                } catch (InterruptedException ignored) {
                }
            } catch (Exception e) {
                switch (settings.log.level) {
                    case MINIMAL -> {}
                    case NORMAL -> System.err.println(e);
                    case VERBOSE -> e.printStackTrace(System.err);
                }
                exceptionMessage = getExceptionMessage(e);
            }
        }

        timer.stop(run.partitionCount(), run.rowCount(), !success);

        if (!success) {
            String detail;
            if (exceptionMessage != null) detail = "Error executing: " + exceptionMessage;
            else {
                String validationMsg = run.validationErrorMessage();
                detail = (validationMsg != null) ? validationMsg : "Data returned was not validated";
            }
            error(String.format(Locale.ROOT, "Operation x%d on key(s) %s: %s%n", tries, key(), detail));
        }
    }

    public abstract String key();

    protected String getExceptionMessage(Exception e) {
        String className = e.getClass().getSimpleName();
        String message = e.getMessage();
        return (message == null) ? "(" + className + ")" : String.format(Locale.ROOT, "(%s): %s", className, message);
    }

    protected void error(String message) throws IOException {
        if (!settings.errors.ignore) throw new IOException(message);
        else if (settings.log.level.compareTo(SettingsLog.Level.MINIMAL) > 0) System.err.println(message);
    }

    public void intendedStartNs(long intendedTime) {
        timer.intendedTimeNs(intendedTime);
    }
}
