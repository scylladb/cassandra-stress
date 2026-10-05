package org.apache.cassandra.stress.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class MultiResultLoggerTest {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private PrintStream captured() {
        return new PrintStream(output, true);
    }

    @Test
    void printsToTheInitialStream() {
        new MultiResultLogger(captured()).println("result");
        assertEquals("result\n", output.toString(UTF_8));
    }

    @Test
    void printsExceptionsWithTheirStackTrace() {
        new MultiResultLogger(captured()).printException(new RuntimeException("Bad things"));
        assertTrue(output.toString(UTF_8).startsWith("java.lang.RuntimeException: Bad things\n\tat "));
    }

    @Test
    void printsToAddedStreams() {
        MultiResultLogger logger = new MultiResultLogger(new PrintStream(OutputStream.nullOutputStream()));
        logger.addStream(captured());

        logger.println("result");
        logger.printf("%s %s", "one", "two");
        logger.println();

        assertEquals("result\none two\n", output.toString(UTF_8));
    }
}
