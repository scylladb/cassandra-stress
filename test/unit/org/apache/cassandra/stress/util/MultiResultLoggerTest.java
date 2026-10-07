package org.apache.cassandra.stress.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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

    @Test
    void closeFlushesAndClosesTheOwnedStreams() {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        PrintStream owned = new PrintStream(new BufferedOutputStream(file), false, UTF_8);
        MultiResultLogger logger = new MultiResultLogger(new PrintStream(OutputStream.nullOutputStream()));
        logger.addOwnedStream(owned);
        logger.println("result");

        logger.close();

        assertEquals("result\n", file.toString(UTF_8));
        owned.println("after close");
        assertTrue(owned.checkError());
    }

    @Test
    void closeLeavesTheInitialAndTheBorrowedStreamsOpen() {
        PrintStream initial = captured();
        ByteArrayOutputStream borrowedBytes = new ByteArrayOutputStream();
        PrintStream borrowed = new PrintStream(borrowedBytes, true, UTF_8);
        MultiResultLogger logger = new MultiResultLogger(initial);
        logger.addStream(borrowed);

        logger.close();

        initial.println("still open");
        borrowed.println("still open");
        assertFalse(initial.checkError());
        assertFalse(borrowed.checkError());
        assertEquals("still open\n", output.toString(UTF_8));
        assertEquals("still open\n", borrowedBytes.toString(UTF_8));
    }

    @Test
    void closeTwiceIsSafeAndLaterLinesReachOnlyTheInitialStream() {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        MultiResultLogger logger = new MultiResultLogger(captured());
        logger.addOwnedStream(new PrintStream(file, true, UTF_8));

        logger.close();
        logger.close();
        logger.println("after close");

        assertEquals("after close\n", output.toString(UTF_8));
        assertEquals("", file.toString(UTF_8));
    }

    @Test
    void writesTheFailureOnlyToTheOwnedStreams() {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        MultiResultLogger logger = new MultiResultLogger(captured());
        logger.addOwnedStream(new PrintStream(file, true, UTF_8));

        logger.printFailureToOwnedStreams(new IllegalStateException("keyspace creation failed"));

        assertEquals("", output.toString(UTF_8));
        assertTrue(file.toString(UTF_8).startsWith("java.lang.IllegalStateException: keyspace creation failed\n"));
    }

    @Test
    void closeWhileOtherThreadsWriteThrowsNothing() throws Exception {
        for (int round = 0; round < 50; round++) {
            MultiResultLogger logger = new MultiResultLogger(new PrintStream(OutputStream.nullOutputStream()));
            for (int i = 0; i < 4; i++) logger.addOwnedStream(new PrintStream(OutputStream.nullOutputStream()));
            CountDownLatch writing = new CountDownLatch(4);
            List<CompletableFuture<Void>> writers = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                writers.add(CompletableFuture.runAsync(() -> {
                    writing.countDown();
                    for (int line = 0; line < 2_000; line++) {
                        logger.println("line");
                        logger.printException(new RuntimeException("failure"));
                    }
                }));
            }
            writing.await();
            logger.close();
            for (CompletableFuture<Void> writer : writers) assertDoesNotThrow(() -> writer.get());
        }
    }
}
