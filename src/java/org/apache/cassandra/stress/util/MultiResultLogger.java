// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("PMD.CloseResource")
public class MultiResultLogger implements ResultLogger, AutoCloseable {
    private final List<PrintStream> streams = new CopyOnWriteArrayList<>();
    private final List<PrintStream> owned = new CopyOnWriteArrayList<>();

    public MultiResultLogger(PrintStream printStream) {
        streams.add(printStream);
    }

    @Override
    public void println(String line) {
        for (PrintStream stream : streams) {
            stream.println(line);
        }
    }

    @Override
    public void println() {
        for (PrintStream stream : streams) {
            stream.println();
        }
    }

    @Override
    public void printException(Exception e) {
        for (PrintStream stream : streams) {
            e.printStackTrace(stream);
        }
    }

    @Override
    public void flush() {
        for (PrintStream stream : streams) {
            stream.flush();
        }
    }

    @Override
    public void printf(String s, Object... args) {
        for (PrintStream stream : streams) {
            stream.printf(Locale.ROOT, s, args);
        }
    }

    public void addStream(PrintStream additionalPrintStream) {
        streams.add(additionalPrintStream);
    }

    public void addOwnedStream(PrintStream ownedPrintStream) {
        owned.add(ownedPrintStream);
        streams.add(ownedPrintStream);
    }

    public void printFailureToOwnedStreams(Throwable failure) {
        for (PrintStream stream : owned) {
            failure.printStackTrace(stream);
        }
    }

    @Override
    public void close() {
        flush();
        streams.removeAll(owned);
        for (PrintStream stream : owned) {
            stream.close();
        }
        owned.clear();
    }
}
