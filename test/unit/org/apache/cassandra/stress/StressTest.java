package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StressTest {
    @TempDir
    Path dir;

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static String[] failingRun(String... options) throws IOException {
        String[] base = {"write", "n=1", "-send-to", "127.0.0.1:" + closedPort()};
        String[] arguments = new String[base.length + options.length];
        System.arraycopy(base, 0, arguments, 0, base.length);
        System.arraycopy(options, 0, arguments, base.length, options.length);
        return arguments;
    }

    @Test
    void theLogFileRecordsWhyTheRunFailed() throws Exception {
        Path log = dir.resolve("stress.log");
        String[] arguments = failingRun("-log", "file=" + log);
        StressSettings settings = StressSettings.parse(arguments);

        assertThrows(ConnectException.class, () -> Stress.run(settings, arguments));

        String written = Files.readString(log);
        assertTrue(written.contains("******************** Stress Settings ********************"), written);
        assertTrue(written.contains("java.net.ConnectException"), written);
    }

    @Test
    void theOutputGoesBackToStandardOutputAfterTheRun() throws Exception {
        Path log = dir.resolve("stress.log");
        String[] arguments = failingRun("-log", "file=" + log);
        StressSettings settings = StressSettings.parse(arguments);

        assertThrows(ConnectException.class, () -> Stress.run(settings, arguments));
        long size = Files.size(log);
        settings.output().println("after the run");

        assertInstanceOf(MultiResultLogger.class, settings.output());
        assertTrue(Files.size(log) == size, "a line after the run reached the closed log file");
    }

    @Test
    void theGraphTemporaryFileIsDeletedAfterTheRun() throws Exception {
        String[] arguments = failingRun("-graph", "file=" + dir.resolve("graph.html"));
        StressSettings settings = StressSettings.parse(arguments);
        Path temporary = settings.graph.temporaryLogFile;
        assertTrue(Files.exists(temporary));

        assertThrows(ConnectException.class, () -> Stress.run(settings, arguments));

        assertFalse(Files.exists(temporary));
    }
}
