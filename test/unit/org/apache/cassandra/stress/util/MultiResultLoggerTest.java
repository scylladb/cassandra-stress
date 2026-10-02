// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MultiResultLoggerTest
{
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private PrintStream captured()
    {
        return new PrintStream(output, true);
    }

    private static PrintStream discarded()
    {
        return new PrintStream(OutputStream.nullOutputStream());
    }

    @Test
    public void printsToTheInitialStream()
    {
        new MultiResultLogger(captured()).println("result");
        assertEquals("result\n", output.toString());
    }

    @Test
    public void printsExceptionsWithTheirStackTrace()
    {
        new MultiResultLogger(captured()).printException(new RuntimeException("Bad things"));
        assertTrue(output.toString().startsWith("java.lang.RuntimeException: Bad things\n\tat "));
    }

    @Test
    public void printsToAddedStreams()
    {
        MultiResultLogger logger = new MultiResultLogger(discarded());
        logger.addStream(captured());

        logger.println("result");
        logger.printf("%s %s", "one", "two");
        logger.println();

        assertEquals("result\none two\n", output.toString());
    }
}
