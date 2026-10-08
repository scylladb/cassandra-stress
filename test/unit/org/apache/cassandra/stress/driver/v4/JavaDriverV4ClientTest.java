package org.apache.cassandra.stress.driver.v4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.cassandra.stress.driver.StressPage;
import org.apache.cassandra.stress.driver.StressPreparedStatement;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.apache.cassandra.stress.util.HostAndPort;
import org.junit.jupiter.api.Test;

class JavaDriverV4ClientTest {
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
        Field field = JavaDriverV4Client.class.getDeclaredField("session");
        field.setAccessible(true);
        field.set(client, fake(CqlSession.class, Map.of("execute", page)));

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
        Field field = JavaDriverV4Client.class.getDeclaredField("session");
        field.setAccessible(true);
        field.set(client, session);

        StressPreparedStatement quorum = client.prepare("SELECT * FROM t WHERE pk = ?");
        StressPreparedStatement one = client.prepare("SELECT * FROM t WHERE pk = ?");
        quorum.setConsistencyLevel(ConsistencyLevel.QUORUM);
        one.setConsistencyLevel(ConsistencyLevel.ONE);

        assertEquals(ConsistencyLevel.QUORUM, quorum.getConsistencyLevel());
        assertEquals(ConsistencyLevel.ONE, one.getConsistencyLevel());
        assertEquals(1, prepared.get());
    }
}
