package org.apache.cassandra.stress.driver.v4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLEngine;
import org.apache.cassandra.stress.driver.StressPage;
import org.apache.cassandra.stress.driver.StressPreparedStatement;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JavaDriverV4ClientTest {
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

    private final CodecRegistry registry = JavaDriverV4Client.codecRegistry();

    @Test
    void bindsGeneratedDatesTimesAndTimestamps() {
        assertEquals(
                TypeCodecs.DATE.encode(LocalDate.ofEpochDay(42), ProtocolVersion.V4),
                registry.codecFor(DataTypes.DATE, 42).encode(42, ProtocolVersion.V4));
        assertEquals(
                TypeCodecs.TIME.encode(LocalTime.ofNanoOfDay(7L), ProtocolVersion.V4),
                registry.codecFor(DataTypes.TIME, 7L).encode(7L, ProtocolVersion.V4));
        assertEquals(
                TypeCodecs.TIMESTAMP.encode(new Date(5L).toInstant(), ProtocolVersion.V4),
                registry.codecFor(DataTypes.TIMESTAMP, new Date(5L)).encode(new Date(5L), ProtocolVersion.V4));
    }

    @Test
    void bindsGeneratedCollectionsOfDates() {
        LinkedHashSet<Integer> days = new LinkedHashSet<>(List.of(1, 2));
        LinkedHashSet<LocalDate> dates = new LinkedHashSet<>(List.of(LocalDate.ofEpochDay(1), LocalDate.ofEpochDay(2)));
        assertEquals(
                registry.codecFor(DataTypes.setOf(DataTypes.DATE), dates).encode(dates, ProtocolVersion.V4),
                registry.codecFor(DataTypes.setOf(DataTypes.DATE), days).encode(days, ProtocolVersion.V4));
    }

    @Test
    void keepsTheDriverCodecsForDriverTypes() {
        assertEquals(TypeCodecs.DATE, registry.codecFor(DataTypes.DATE, LocalDate.ofEpochDay(1)));
        assertEquals(TypeCodecs.BIGINT, registry.codecFor(DataTypes.BIGINT, 1L));
    }

    @Test
    void whiteListResolvesEveryContactPoint() {
        assertEquals(
                Set.of(InetAddress.getLoopbackAddress()),
                new JavaDriverV4Client.WhiteList(List.of(new HostAndPort("127.0.0.1", 9042))).addresses());
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> new JavaDriverV4Client.WhiteList(List.of(new HostAndPort("no-such-host.invalid", 9042))));
        assertEquals("Cannot resolve the whitelisted node no-such-host.invalid", e.getMessage());
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> answers) {
        return (T) Proxy.newProxyInstance(
                JavaDriverV4ClientTest.class.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
                    if (answers.containsKey(method.getName())) {
                        return answers.get(method.getName());
                    }
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName();
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(type.getSimpleName() + "." + method.getName());
                });
    }

    @Test
    void anEmptyPageWithAPagingStateReadsNoFurtherRows() throws Exception {
        AtomicInteger rowsRead = new AtomicInteger();
        Iterator<Row> rows = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Row next() {
                rowsRead.incrementAndGet();
                throw new IllegalStateException("the next page was fetched");
            }
        };
        ByteBuffer state = ByteBuffer.allocate(4);
        ResultSet page = fake(
                ResultSet.class,
                Map.of(
                        "getExecutionInfo",
                        fake(ExecutionInfo.class, Map.of("getPagingState", state)),
                        "getAvailableWithoutFetching",
                        0,
                        "iterator",
                        rows,
                        "getColumnDefinitions",
                        fake(
                                ColumnDefinitions.class,
                                Map.of("size", 0, "iterator", List.of().iterator())),
                        "isFullyFetched",
                        false));
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "4x"});
        JavaDriverV4Client client =
                new JavaDriverV4Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());
        client.useSession(fake(CqlSession.class, Map.of("execute", page)));

        StressPage result = client.executePage("SELECT * FROM t", 10, null);

        assertEquals(0, rowsRead.get());
        assertEquals(0, result.result().rows().size());
        assertEquals(state, result.pagingState());
        assertEquals(false, result.fullyFetched());
    }

    @Test
    void eachPreparedQueryKeepsItsOwnConsistencyLevel() throws Exception {
        AtomicInteger prepared = new AtomicInteger();
        PreparedStatement driverStatement = fake(
                PreparedStatement.class,
                Map.of(
                        "getQuery",
                        "SELECT * FROM t WHERE pk = ?",
                        "getVariableDefinitions",
                        fake(
                                ColumnDefinitions.class,
                                Map.of("iterator", List.of().iterator()))));
        CqlSession session = (CqlSession) Proxy.newProxyInstance(
                JavaDriverV4ClientTest.class.getClassLoader(),
                new Class<?>[] {CqlSession.class},
                (proxy, method, args) -> {
                    if (!"prepare".equals(method.getName())) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    prepared.incrementAndGet();
                    return driverStatement;
                });
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "4x"});
        JavaDriverV4Client client =
                new JavaDriverV4Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());
        client.useSession(session);

        StressPreparedStatement quorum = client.prepare("SELECT * FROM t WHERE pk = ?");
        StressPreparedStatement one = client.prepare("SELECT * FROM t WHERE pk = ?");
        quorum.setConsistencyLevel(ConsistencyLevel.QUORUM);
        one.setConsistencyLevel(ConsistencyLevel.ONE);

        assertEquals(ConsistencyLevel.QUORUM, quorum.getConsistencyLevel());
        assertEquals(ConsistencyLevel.ONE, one.getConsistencyLevel());
        assertEquals(1, prepared.get());
    }

    @ParameterizedTest
    @CsvSource({"true, HTTPS", "false,"})
    void theTlsEngineNamesThePeerByItsAddressWithoutReverseDns(boolean verify, String algorithm) throws Exception {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "4x"});
        JavaDriverV4Client client = new JavaDriverV4Client(settings, List.of("127.0.0.1"), 9042, tls(verify));

        SSLEngine engine = client.sslEngineFactory().newSslEngine(new DefaultEndPoint(ipOnly()));

        assertEquals("127.0.0.1", engine.getPeerHost());
        assertEquals(9042, engine.getPeerPort());
        assertEquals(algorithm, engine.getSSLParameters().getEndpointIdentificationAlgorithm());
    }

    @Test
    void speculationConfiguresTheConstantPolicyWithTheTotalExecutions() {
        StressSettings settings = StressSettings.parse(new String[] {
            "write", "n=1", "-mode", "cql3", "4x", "idempotent", "speculativeExecutions=3", "speculativeDelay=500"
        });
        JavaDriverV4Client client =
                new JavaDriverV4Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());

        DriverExecutionProfile profile = client.config(ProtocolCompression.NONE)
                .build()
                .getInitialConfig()
                .getDefaultProfile();

        assertEquals(
                "ConstantSpeculativeExecutionPolicy",
                profile.getString(DefaultDriverOption.SPECULATIVE_EXECUTION_POLICY_CLASS));
        assertEquals(3, profile.getInt(DefaultDriverOption.SPECULATIVE_EXECUTION_MAX));
        assertEquals(Duration.ofMillis(500), profile.getDuration(DefaultDriverOption.SPECULATIVE_EXECUTION_DELAY));
        assertEquals(Duration.ofMillis(10), profile.getDuration(DefaultDriverOption.NETTY_TIMER_TICK_DURATION));
    }

    @Test
    void theDriverShutsDownWithoutAQuietPeriod() {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "4x"});
        JavaDriverV4Client client =
                new JavaDriverV4Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());

        DriverExecutionProfile profile = client.config(ProtocolCompression.NONE)
                .build()
                .getInitialConfig()
                .getDefaultProfile();

        assertEquals(0, profile.getInt(DefaultDriverOption.NETTY_IO_SHUTDOWN_QUIET_PERIOD));
        assertEquals(0, profile.getInt(DefaultDriverOption.NETTY_ADMIN_SHUTDOWN_QUIET_PERIOD));
    }

    @Test
    void withoutSpeculationTheDriverKeepsItsDefaultPolicy() {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "4x"});
        JavaDriverV4Client client =
                new JavaDriverV4Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());

        DriverExecutionProfile profile = client.config(ProtocolCompression.NONE)
                .build()
                .getInitialConfig()
                .getDefaultProfile();

        assertEquals(
                "NoSpeculativeExecutionPolicy",
                profile.getString(DefaultDriverOption.SPECULATIVE_EXECUTION_POLICY_CLASS));
    }

    @Test
    void aListBindVariableIsAList() {
        assertTrue(V4PreparedStatement.isList(DataTypes.listOf(DataTypes.INT)));
        assertTrue(V4PreparedStatement.isList(DataTypes.frozenListOf(DataTypes.INT)));
        assertFalse(V4PreparedStatement.isList(DataTypes.setOf(DataTypes.INT)));
        assertFalse(V4PreparedStatement.isList(DataTypes.INT));
    }

    @Test
    void markIdempotentSetsTheDriverFlag() {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "4x"});
        JavaDriverV4Client client =
                new JavaDriverV4Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());

        assertTrue(client.markIdempotent(SimpleStatement.newInstance("SELECT * FROM t"))
                .isIdempotent());
    }

    @Test
    void disconnectingAClientThatNeverConnectedPrintsNothing() {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-mode", "cql3", "4x"});
        settings.setOutput(new MultiResultLogger(new PrintStream(printed, true, StandardCharsets.UTF_8)));

        new JavaDriverV4Client(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions()).disconnect();

        assertEquals("", printed.toString(StandardCharsets.UTF_8));
    }
}
