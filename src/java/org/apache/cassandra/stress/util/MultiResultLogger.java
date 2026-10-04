// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class MultiResultLogger implements ResultLogger
{
    private final List<PrintStream> streams = new ArrayList<>();

    public MultiResultLogger(PrintStream printStream)
    {
        streams.add(printStream);
    }

    public void println(String line)
    {
        for (PrintStream stream : streams)
        {
            stream.println(line);
        }
    }

    public void println()
    {
        for (PrintStream stream : streams)
        {
            stream.println();
        }
    }

    public void printException(Exception e)
    {
        for (PrintStream stream : streams)
        {
            e.printStackTrace(stream);
        }
    }

    public void flush()
    {
        for (PrintStream stream : streams)
        {
            stream.flush();
        }
    }

    public void printf(String s, Object... args)
    {
        for (PrintStream stream : streams)
        {
            stream.printf(s, args);
        }
    }

    public void addStream(PrintStream additionalPrintStream)
    {
        streams.add(additionalPrintStream);
    }
}
