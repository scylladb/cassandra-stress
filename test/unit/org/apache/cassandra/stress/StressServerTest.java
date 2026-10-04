package org.apache.cassandra.stress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.cassandra.stress.settings.StressSettings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StressServerTest
{
    private static String[] roundTrip(String[] arguments) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StressServer.writeCommand(new DataOutputStream(bytes), arguments);
        return StressServer.readCommand(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    private static DataInputStream countOnly(int count) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt(count);
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test
    void listensOnLocalhostByDefault()
    {
        assertEquals("127.0.0.1", StressServer.listenHost(new String[]{ "start" }));
    }

    @ParameterizedTest
    @CsvSource({ "10.0.0.1, start -h 10.0.0.1", "10.0.0.2, --host 10.0.0.2", "10.0.0.3, --host=10.0.0.3" })
    void readsTheHostInEachForm(String host, String args)
    {
        assertEquals(host, StressServer.listenHost(args.split(" ")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "-h", "-x 1" })
    void rejectsAMissingHostOrAnUnknownOption(String args)
    {
        assertNull(StressServer.listenHost(args.split(" ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "mixed ratio(write=1,read=2) n=10 cl=QUORUM -rate threads=4 -pop seq=1..10 -col n=fixed(3) size=uniform(1..20) -node 127.0.0.1 -mode cql3 4x -send-to 127.0.0.1",
        "read duration=1m -log hdrfile=/tmp/stress.hdr -mode cql3 native user=u password=p",
        "counter_write n=10 -rate threads=2 fixed=100/s -errors retries=3",
    })
    void serverParsesTheCommandTheClientSends(String command) throws IOException
    {
        String[] arguments = command.split(" ");
        String[] received = roundTrip(arguments);
        assertArrayEquals(arguments, received);
        assertEquals(StressSettings.parse(arguments.clone()).command.type, StressSettings.parse(received).command.type);
    }

    @Test
    void keepsNonAsciiArguments() throws IOException
    {
        String[] arguments = { "write", "-graph", "file=/tmp/zażółć.html", "title=✓" };
        assertArrayEquals(arguments, roundTrip(arguments));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, StressServer.MAX_ARGUMENTS + 1, Integer.MAX_VALUE })
    void rejectsAnInvalidArgumentCount(int count)
    {
        assertThrows(IOException.class, () -> StressServer.readCommand(countOnly(count)));
    }

    @Test
    void rejectsATruncatedCommand()
    {
        assertThrows(IOException.class, () -> StressServer.readCommand(countOnly(2)));
    }
}
