package org.apache.cassandra.stress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.cassandra.stress.settings.StressSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StressServerTest
{
    private static byte[] serialize(Serializable value) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes))
        {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object readFiltered(byte[] bytes) throws IOException, ClassNotFoundException
    {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes)))
        {
            in.setObjectInputFilter(StressServer.SETTINGS_FILTER);
            return in.readObject();
        }
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
        "mixed ratio(write=1,read=2) n=10 cl=QUORUM -rate threads=4 -pop seq=1..10 -col n=fixed(3) size=uniform(1..20) -node 127.0.0.1 -schema replication(factor=3) -mode cql3 4x -transport truststore=/tmp/ts.jks",
        "read duration=1m -log hdrfile=/tmp/stress.hdr -graph file=/tmp/stress.html -mode cql3 native user=u password=p",
        "counter_write n=10 -rate threads=2 fixed=100/s -errors retries=3",
    })
    void filterAcceptsPredefinedSettings(String args) throws Exception
    {
        StressSettings settings = StressSettings.parse(args.split(" "));
        StressSettings copy = (StressSettings) readFiltered(serialize(settings));
        assertEquals(settings.command.type, copy.command.type);
    }

    @Test
    void filterRejectsOtherClasses()
    {
        assertThrows(InvalidClassException.class, () -> readFiltered(serialize(new AtomicLong(1))));
    }
}
