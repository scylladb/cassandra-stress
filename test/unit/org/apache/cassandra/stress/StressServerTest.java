package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.HostAndPort;
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
}
