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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.junit.jupiter.api.Test;
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
            boolean succeeded = Stress.sendToDaemon(
                    new HostAndPort("127.0.0.1", daemon.getLocalPort()),
                    new String[] {"write", "n=1"},
                    new MultiResultLogger(new PrintStream(OutputStream.nullOutputStream())));
            served.get(10, TimeUnit.SECONDS);
            return succeeded;
        }
    }

    @Test
    void theClientSucceedsOnlyWhenTheDaemonEnds() throws Exception {
        assertTrue(sendThroughFakeDaemon("Results:", "END"));
        assertFalse(sendThroughFakeDaemon("Invalid parameter bogus", "FAILURE"));
        assertFalse(sendThroughFakeDaemon("Results:"));
    }

    @Test
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
