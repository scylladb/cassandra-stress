package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class StressServerTest {
    private static String[] roundTrip(String[] arguments) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StressServer.writeCommand(new DataOutputStream(bytes), arguments);
        return StressServer.readCommand(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    private static DataInputStream countOnly(int count) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt(count);
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test
    void listensOnLocalhostAndTheDefaultPort() {
        assertEquals(
                new HostAndPort("127.0.0.1", StressServer.DEFAULT_PORT),
                StressServer.listenAddress(new String[] {"start"}));
    }

    @ParameterizedTest
    @CsvSource({
        "10.0.0.1, 2159, start -h 10.0.0.1",
        "10.0.0.2, 2159, --host 10.0.0.2",
        "10.0.0.3, 2159, --host=10.0.0.3",
        "127.0.0.1, 3000, start -p 3000",
        "10.0.0.4, 3001, --host 10.0.0.4 --port 3001",
        "127.0.0.1, 3002, --port=3002",
    })
    void readsTheHostAndPortInEachForm(String host, int port, String args) {
        assertEquals(new HostAndPort(host, port), StressServer.listenAddress(args.split(" ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-h", "-x 1", "-p"})
    void rejectsAMissingValueOrAnUnknownOption(String args) {
        assertNull(StressServer.listenAddress(args.split(" ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-p x", "--port=0", "--port=70000"})
    void rejectsAnInvalidPort(String args) {
        assertThrows(IllegalArgumentException.class, () -> StressServer.listenAddress(args.split(" ")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "mixed ratio(write=1,read=2) n=10 cl=QUORUM -rate threads=4 -pop seq=1..10 -col n=fixed(3)"
                        + " size=uniform(1..20) -node 127.0.0.1 -mode cql3 4x -send-to 127.0.0.1",
                "read duration=1m -log hdrfile=/tmp/stress.hdr -mode cql3 native user=u password=p",
                "counter_write n=10 -rate threads=2 fixed=100/s -errors retries=3",
            })
    void serverParsesTheCommandTheClientSends(String command) throws IOException {
        String[] arguments = command.split(" ");
        String[] received = roundTrip(arguments);
        assertArrayEquals(arguments, received);
        assertEquals(StressSettings.parse(arguments.clone()).command.type, StressSettings.parse(received).command.type);
    }

    @Test
    void keepsNonAsciiArguments() throws IOException {
        String[] arguments = {"write", "-graph", "file=/tmp/zażółć.html", "title=✓"};
        assertArrayEquals(arguments, roundTrip(arguments));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, StressServer.MAX_ARGUMENTS + 1, Integer.MAX_VALUE})
    void rejectsAnInvalidArgumentCount(int count) {
        assertThrows(IOException.class, () -> StressServer.readCommand(countOnly(count)));
    }

    @Test
    void rejectsATruncatedCommand() {
        assertThrows(IOException.class, () -> StressServer.readCommand(countOnly(2)));
    }

    private static List<String> sendToServer(String... arguments) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> served = CompletableFuture.runAsync(() -> {
                try {
                    StressServer.serve(server.accept());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            try (Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
                    BufferedReader reply = new BufferedReader(
                            new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                StressServer.writeCommand(new DataOutputStream(client.getOutputStream()), arguments);
                List<String> lines = reply.lines().toList();
                served.get(10, TimeUnit.SECONDS);
                return lines;
            }
        }
    }

    @Test
    void answersAnInvalidCommandAndKeepsRunning() throws Exception {
        assertEquals(
                List.of("Invalid -rate options provided, see output for valid options", "FAILURE"),
                sendToServer("write", "n=10", "-rate", "threads=4", "auto"));
        assertEquals(List.of("Invalid parameter bogus", "FAILURE"), sendToServer("write", "n=10", "-rate", "bogus"));
    }

    @FunctionalInterface
    interface RawClient {
        void send(DataOutputStream out, Socket socket) throws Exception;
    }

    @SuppressWarnings("PMD.CloseResource")
    private static List<String> sendRaw(Duration commandTimeout, RawClient sender) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> served = CompletableFuture.runAsync(() -> {
                try {
                    StressServer.serve(server.accept(), commandTimeout);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            try (Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
                    BufferedReader reply = new BufferedReader(
                            new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                AtomicReference<Exception> writeError = new AtomicReference<>();
                Thread writer = Thread.ofVirtual().start(() -> {
                    try {
                        sender.send(out, client);
                        out.flush();
                    } catch (Exception e) {
                        writeError.set(e);
                    }
                });
                List<String> lines = reply.lines().toList();
                served.get(10, TimeUnit.SECONDS);
                writer.interrupt();
                return lines;
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, StressServer.MAX_ARGUMENTS + 1})
    void answersFailureForAnInvalidArgumentCount(int count) throws Exception {
        assertEquals(
                List.of("Invalid argument count: " + count, "FAILURE"),
                sendRaw(Duration.ofSeconds(5), (out, socket) -> out.writeInt(count)));
    }

    @Test
    void answersFailureForACommandThatEndsEarly() throws Exception {
        assertEquals(
                List.of("The command ended before its last argument", "FAILURE"),
                sendRaw(Duration.ofSeconds(5), (out, socket) -> {
                    out.writeInt(2);
                    out.writeUTF("write");
                    out.flush();
                    socket.shutdownOutput();
                }));
    }

    @Test
    void answersFailureForAnArgumentThatIsNotUtf8() throws Exception {
        assertEquals(
                List.of("Argument 2 is not valid modified UTF-8", "FAILURE"),
                sendRaw(Duration.ofSeconds(5), (out, socket) -> {
                    out.writeInt(2);
                    out.writeUTF("write");
                    out.writeShort(1);
                    out.writeByte(0xFF);
                }));
    }

    @Test
    void stopsReadingACommandAtItsDeadlineAlsoWhenBytesKeepArriving() throws Exception {
        long started = System.nanoTime();
        List<String> reply = sendRaw(Duration.ofMillis(500), (out, socket) -> {
            out.writeInt(1);
            out.writeShort(100);
            for (int i = 0; i < 100; i++) {
                out.writeByte('a');
                out.flush();
                Thread.sleep(100);
            }
        });
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals(List.of("The command took longer than 500 ms", "FAILURE"), reply);
        assertTrue(elapsedMillis < 5_000, "the daemon read for " + elapsedMillis + " ms");
    }

    private static String[] commandOfBytes(int bytes) {
        int fullArguments = 15;
        int fixed = Integer.BYTES + Short.BYTES + "write".length() + (fullArguments + 1) * Short.BYTES;
        String[] arguments = new String[fullArguments + 2];
        arguments[0] = "write";
        for (int i = 1; i <= fullArguments; i++) {
            arguments[i] = "a".repeat(65_535);
        }
        arguments[fullArguments + 1] = "b".repeat(bytes - fixed - fullArguments * 65_535);
        return arguments;
    }

    private static String parseError(String[] arguments) {
        return assertThrows(RuntimeException.class, () -> StressSettings.parseForDaemon(arguments.clone()))
                .getMessage();
    }

    @Test
    void readsACommandOfExactlyTheByteLimit() throws Exception {
        String[] arguments = commandOfBytes(StressServer.MAX_COMMAND_BYTES);
        assertEquals(
                List.of(parseError(arguments), "FAILURE"),
                sendRaw(Duration.ofSeconds(30), (out, socket) -> StressServer.writeCommand(out, arguments)));
    }

    @Test
    void refusesACommandOneByteOverTheLimit() throws Exception {
        String[] arguments = commandOfBytes(StressServer.MAX_COMMAND_BYTES + 1);
        assertEquals(
                List.of("The command is longer than " + StressServer.MAX_COMMAND_BYTES + " bytes", "FAILURE"),
                sendRaw(Duration.ofSeconds(30), (out, socket) -> StressServer.writeCommand(out, arguments)));
    }

    @Test
    void namesTheExceptionWhenItHasNoMessage() {
        assertEquals("IllegalArgumentException", StressServer.failureMessage(new IllegalArgumentException()));
        assertEquals("bad", StressServer.failureMessage(new IllegalArgumentException("bad")));
    }

    @Test
    void answersFailureForAnyErrorWhileParsing() throws Exception {
        List<String> reply = sendToServer("write", "n=10", "-pop", "seq=10..1");
        assertEquals("FAILURE", reply.getLast());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "stressd refuses -node file=. Pass the nodes as a list. | write n=10 -node file=/etc/hosts",
                "stressd refuses -log hdrfile=.                         | write n=10 -log hdrfile=/tmp/x.hdr",
                "stressd refuses -log hdrfile=.                         | write n=10 -log HDRFILE=/tmp/x.hdr",
                "stressd refuses -node file=. Pass the nodes as a list. | write n=10 -node FILE=/etc/hosts",
                "stressd runs the predefined commands only.            | user profile=/etc/passwd ops(insert=1)",
            })
    void refusesOptionsThatReadOrWriteFilesOnTheDaemonHost(String message, String command) throws Exception {
        assertEquals(List.of(message, "FAILURE"), sendToServer(command.split(" ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"n=0", "n=10"})
    void answersFailureWhenTheActionCannotConnect(String count) throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        List<String> reply = assertTimeoutPreemptively(
                Duration.ofSeconds(60),
                () -> sendToServer("write", count, "-node", "127.0.0.1", "-port", "native=" + closedPort));
        assertEquals("FAILURE", reply.getLast(), reply.toString());
        assertTrue(reply.get(reply.size() - 2).startsWith("Stress action failed: "), reply.toString());
    }

    @Test
    void namesTheReasonWhenTheRunFailsWithoutAnException() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        List<String> reply = assertTimeoutPreemptively(
                Duration.ofSeconds(60),
                () -> sendToServer("read", "n=10", "no-warmup", "-node", "127.0.0.1", "-port", "native=" + closedPort));
        assertEquals(
                List.of("Stress action failed: An operation failed", "FAILURE"),
                reply.subList(reply.size() - 2, reply.size()),
                reply.toString());
    }

    @Test
    void answersTheSameForAMissingTruststoreAndAFileThatIsNoKeyStore(@TempDir Path dir) throws Exception {
        Path notAStore = Files.writeString(dir.resolve("hosts"), "127.0.0.1 localhost\n");
        Path missing = dir.resolve("missing.jks");
        List<List<String>> replies = new ArrayList<>();

        for (Path truststore : List.of(notAStore, missing)) {
            List<String> reply =
                    sendToServer("write", "n=0", "-node", "127.0.0.1", "-transport", "truststore=" + truststore);
            replies.add(reply.subList(reply.size() - 2, reply.size()));
        }

        List<String> expected =
                List.of("Stress action failed: java.io.IOException: Could not create the SSL context", "FAILURE");
        assertEquals(List.of(expected, expected), replies);
    }

    @Test
    void leavesTheClientSideLogAndGraphFilesToTheClient(@TempDir Path dir) throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        Path log = dir.resolve("client.log");
        Path graph = dir.resolve("client.html");

        List<String> reply = assertTimeoutPreemptively(
                Duration.ofSeconds(60),
                () -> sendToServer(
                        "write",
                        "n=0",
                        "-node",
                        "127.0.0.1",
                        "-port",
                        "native=" + closedPort,
                        "-log",
                        "file=" + log,
                        "-graph",
                        "file=" + graph));

        assertEquals("FAILURE", reply.getLast(), reply.toString());
        assertFalse(Files.exists(log), "stressd wrote the -log file= of the client");
        assertFalse(Files.exists(graph), "stressd wrote the -graph file= of the client");
        try (var files = Files.list(dir)) {
            assertEquals(List.of(), files.toList());
        }
    }

    @Test
    void refusesAClientAboveTheLimit() throws Exception {
        StressServer.CLIENTS.acquire(StressServer.MAX_CLIENTS);
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
                BufferedReader reply =
                        new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
            StressServer.accept(server.accept());
            assertEquals(
                    List.of(
                            "stressd serves " + StressServer.MAX_CLIENTS + " clients at a time. Try again later.",
                            "FAILURE"),
                    reply.lines().toList());
        } finally {
            StressServer.CLIENTS.release(StressServer.MAX_CLIENTS);
        }
    }

    private static boolean sendThroughFakeDaemon(String... reply) throws Exception {
        try (ServerSocket daemon = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> served = CompletableFuture.runAsync(() -> {
                try (Socket client = daemon.accept();
                        PrintStream out = new PrintStream(client.getOutputStream(), true, StandardCharsets.UTF_8)) {
                    StressServer.readCommand(new DataInputStream(client.getInputStream()));
                    for (String line : reply) {
                        out.println(line);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            try (PrintStream discarded =
                    new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8)) {
                boolean succeeded = Stress.sendToDaemon(
                        new HostAndPort("127.0.0.1", daemon.getLocalPort()),
                        new String[] {"write", "n=1"},
                        new MultiResultLogger(discarded));
                served.get(10, TimeUnit.SECONDS);
                return succeeded;
            }
        }
    }

    @Test
    void theClientSucceedsOnlyWhenTheDaemonEnds() throws Exception {
        assertTrue(sendThroughFakeDaemon("Results:", "END"));
        assertFalse(sendThroughFakeDaemon("Invalid parameter bogus", "FAILURE"));
        assertFalse(sendThroughFakeDaemon("Results:"));
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    void aCancelledRequestEndsOnlyAfterItsActionStops() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> served = CompletableFuture.runAsync(() -> {
                try {
                    StressServer.serve(server.accept());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            try (Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                StressServer.writeCommand(
                        out, new String[] {"write", "n=10", "-node", "127.0.0.1", "-port", "native=" + closedPort});
                out.writeInt(1);
                out.flush();
                served.get(60, TimeUnit.SECONDS);
            }
            assertTrue(
                    Thread.getAllStackTraces().keySet().stream()
                            .noneMatch(t -> t.getName().startsWith("stress-") && t.isAlive()),
                    "the action thread outlived its request");
        }
    }
}
