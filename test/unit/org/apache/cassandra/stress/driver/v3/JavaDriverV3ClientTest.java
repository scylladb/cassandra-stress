package org.apache.cassandra.stress.driver.v3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.EndPoint;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.policies.SpeculativeExecutionPolicy;
import com.google.common.reflect.TypeToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLEngine;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JavaDriverV3ClientTest {
    @TempDir
    Path dir;

    private EncryptionOptions tls(boolean hostnameVerification) throws Exception {
        Path store = dir.resolve("store.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        try (OutputStream out = Files.newOutputStream(store)) {
            keyStore.store(out, "p".toCharArray());
        }
        EncryptionOptions options = new EncryptionOptions();
        options.enabled = true;
        options.storeType = "PKCS12";
        options.truststore = store.toString();
        options.truststorePassword = "p";
        options.keystore = store.toString();
        options.keystorePassword = "p";
        options.hostnameVerification = hostnameVerification;
        return options;
    }

    private static InetSocketAddress ipOnly() throws Exception {
        return new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9042);
    }

    private final CodecRegistry registry = JavaDriverV3Client.codecRegistry();

    @Test
    void bindsGeneratedDatesTimesAndTimestamps() {
        assertEquals(
                TypeCodec.date().serialize(LocalDate.fromDaysSinceEpoch(42), ProtocolVersion.V4),
                registry.codecFor(DataType.date(), 42).serialize(42, ProtocolVersion.V4));
        assertEquals(
                TypeCodec.time().serialize(7L, ProtocolVersion.V4),
                registry.codecFor(DataType.time(), 7L).serialize(7L, ProtocolVersion.V4));
        assertEquals(
                TypeCodec.timestamp().serialize(new Date(5L), ProtocolVersion.V4),
                registry.codecFor(DataType.timestamp(), new Date(5L)).serialize(new Date(5L), ProtocolVersion.V4));
    }

    @Test
    void bindsGeneratedCollectionsOfDates() {
        Set<Integer> days = new LinkedHashSet<>(List.of(1, 2));
        Set<LocalDate> dates =
                new LinkedHashSet<>(List.of(LocalDate.fromDaysSinceEpoch(1), LocalDate.fromDaysSinceEpoch(2)));
        TypeCodec<Set<LocalDate>> driverCodec = registry.codecFor(DataType.set(DataType.date()), dates);
        TypeCodec<Set<Integer>> generatedCodec =
                registry.codecFor(DataType.set(DataType.date()), new TypeToken<Set<Integer>>() {});
        assertEquals(
                driverCodec.serialize(dates, ProtocolVersion.V4), generatedCodec.serialize(days, ProtocolVersion.V4));
    }

    @ParameterizedTest
    @CsvSource({"true, HTTPS", "false,"})
    void theTlsEngineNamesThePeerByItsAddressWithoutReverseDns(boolean verify, String algorithm) throws Exception {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "native"});
        JavaDriverV3Client client = new JavaDriverV3Client(settings, List.of("127.0.0.1"), 9042, tls(verify));

        InetSocketAddress address = ipOnly();
        EndPoint endPoint = () -> address;

        SSLEngine engine = client.sslOptions().newSSLHandler(null, endPoint).engine();

        assertEquals("127.0.0.1", engine.getPeerHost());
        assertEquals(9042, engine.getPeerPort());
        assertEquals(algorithm, engine.getSSLParameters().getEndpointIdentificationAlgorithm());
    }

    @Test
    void speculationRunsTheTotalExecutionsCountingTheFirst() throws Exception {
        StressSettings settings = StressSettings.parse(new String[] {
            "write", "n=1", "-mode", "cql3", "native", "idempotent", "speculativeExecutions=3", "speculativeDelay=500"
        });
        JavaDriverV3Client client =
                new JavaDriverV3Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());

        SpeculativeExecutionPolicy.SpeculativeExecutionPlan plan = client.builder(ProtocolCompression.NONE)
                .getConfiguration()
                .getPolicies()
                .getSpeculativeExecutionPolicy()
                .newPlan("ks", new SimpleStatement("SELECT * FROM t"));

        assertEquals(
                List.of(500L, 500L, -1L),
                List.of(plan.nextExecution(null), plan.nextExecution(null), plan.nextExecution(null)));
    }

    @Test
    void speculationShortensTheTimerTick() throws Exception {
        StressSettings settings = StressSettings.parse(new String[] {
            "write", "n=1", "-mode", "cql3", "native", "idempotent", "speculativeExecutions=3", "speculativeDelay=500"
        });
        JavaDriverV3Client client =
                new JavaDriverV3Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());

        assertEquals(10, nettyOptions(client).tickMillis());
    }

    @Test
    void withoutSpeculationTheTimerKeepsTheDriverTick() throws Exception {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "native"});
        JavaDriverV3Client client =
                new JavaDriverV3Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());

        assertNull(nettyOptions(client).tickMillis());
    }

    private static JavaDriverV3Client.StressNettyOptions nettyOptions(JavaDriverV3Client client) throws IOException {
        return (JavaDriverV3Client.StressNettyOptions)
                client.builder(ProtocolCompression.NONE).getConfiguration().getNettyOptions();
    }

    @Test
    void withoutSpeculationTheDriverRunsOneExecution() throws Exception {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "native"});
        JavaDriverV3Client client =
                new JavaDriverV3Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());

        SpeculativeExecutionPolicy.SpeculativeExecutionPlan plan = client.builder(ProtocolCompression.NONE)
                .getConfiguration()
                .getPolicies()
                .getSpeculativeExecutionPolicy()
                .newPlan("ks", new SimpleStatement("SELECT * FROM t"));

        assertEquals(-1L, plan.nextExecution(null));
    }

    @Test
    void aListBindVariableIsAList() {
        assertTrue(V3PreparedStatement.isList(DataType.list(DataType.cint())));
        assertTrue(V3PreparedStatement.isList(DataType.frozenList(DataType.cint())));
        assertFalse(V3PreparedStatement.isList(DataType.set(DataType.cint())));
        assertFalse(V3PreparedStatement.isList(DataType.cint()));
    }

    @Test
    void markIdempotentSetsTheDriverFlag() {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "native"});
        JavaDriverV3Client client =
                new JavaDriverV3Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());

        assertTrue(client.markIdempotent(new SimpleStatement("SELECT * FROM t")).isIdempotent());
    }

    @Test
    void disconnectingAClientThatNeverConnectedPrintsNothing() {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "native"});
        settings.setOutput(new MultiResultLogger(new PrintStream(printed, true, StandardCharsets.UTF_8)));

        new JavaDriverV3Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions()).disconnect();

        assertEquals("", printed.toString(StandardCharsets.UTF_8));
    }
}
