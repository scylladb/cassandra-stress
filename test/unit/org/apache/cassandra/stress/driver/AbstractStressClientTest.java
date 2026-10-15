package org.apache.cassandra.stress.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.junit.jupiter.api.Test;

class AbstractStressClientTest {
    record Sent(String cql, ConsistencyLevel consistency, ConsistencyLevel serialConsistency, List<Sent> batch) {}

    private static final class FakePrepared extends AbstractPreparedStatement {
        FakePrepared(String query) {
            super(query, List.of());
        }

        @Override
        public StressBoundStatement bind(Object... values) {
            return () -> this;
        }
    }

    private static final class FakeClient extends AbstractStressClient<String, Sent, List<String>, String> {
        final List<Sent> sent = new ArrayList<>();
        final List<Sent> idempotent = new ArrayList<>();
        final AtomicInteger prepared = new AtomicInteger();
        final AtomicInteger valuesRead = new AtomicInteger();
        List<String> rows = List.of();
        RuntimeException failure;
        int rowsBeforeFetchFailure = -1;

        FakeClient(String... args) {
            super(StressSettings.parse(args), List.of("127.0.0.1"), 9042, new EncryptionOptions());
        }

        @Override
        public void connect(ProtocolCompression compression) {}

        @Override
        public TableSchema tableSchema(String keyspace, String table) {
            return null;
        }

        @Override
        protected String prepareInDriver(String query) {
            prepared.incrementAndGet();
            return query;
        }

        @Override
        protected StressPreparedStatement wrap(String statement) {
            return new FakePrepared(statement);
        }

        @Override
        protected Sent simple(String query, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
            return new Sent(query, consistency, serialConsistency, List.of());
        }

        @Override
        protected Sent page(String query, int pageSize, Object pagingState) {
            return new Sent(query, null, null, List.of());
        }

        @Override
        protected Sent bound(
                StressBoundStatement statement, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
            return new Sent(statement.statement().getQueryString(), consistency, serialConsistency, List.of());
        }

        @Override
        protected Sent batch(
                BatchType type,
                ConsistencyLevel consistency,
                ConsistencyLevel serialConsistency,
                List<Sent> statements) {
            return new Sent("BATCH " + type, consistency, serialConsistency, statements);
        }

        @Override
        protected Sent markIdempotent(Sent statement) {
            idempotent.add(statement);
            return statement;
        }

        @Override
        protected List<String> run(Sent statement) {
            if (failure != null) {
                throw failure;
            }
            sent.add(statement);
            return rows;
        }

        @Override
        protected RuntimeException translate(RuntimeException error) {
            return error instanceof IllegalStateException ? new OverloadedException("overloaded", error) : error;
        }

        @Override
        protected Iterator<String> rows(List<String> results) {
            Iterator<String> rows = results.iterator();
            return new Iterator<>() {
                int read;

                @Override
                public boolean hasNext() {
                    if (read == rowsBeforeFetchFailure) {
                        throw new IllegalStateException("overloaded while fetching the next page");
                    }
                    return rows.hasNext();
                }

                @Override
                public String next() {
                    read++;
                    return rows.next();
                }
            };
        }

        @Override
        protected int availableWithoutFetching(List<String> results) {
            return results.size();
        }

        @Override
        protected List<String> columnNames(List<String> results) {
            return List.of("v");
        }

        @Override
        protected ByteBuffer[] values(String row) {
            valuesRead.incrementAndGet();
            return new ByteBuffer[] {ByteBuffer.wrap(row.getBytes(java.nio.charset.StandardCharsets.UTF_8))};
        }

        @Override
        protected Object pagingState(List<String> results) {
            return null;
        }

        @Override
        protected boolean fullyFetched(List<String> results) {
            return true;
        }

        @Override
        protected List<TokenSlice> ringRanges() {
            return List.of(new TokenSlice(5, 5));
        }

        @Override
        protected void close() {}
    }

    @Test
    void noStatementIsIdempotentWithoutTheOption() {
        FakeClient client = new FakeClient("write", "n=1");
        StressPreparedStatement statement = client.prepare("INSERT INTO t (k) VALUES (?)");

        client.execute("SELECT * FROM t", ConsistencyLevel.ONE, null);
        client.execute(statement.bind(), ConsistencyLevel.ONE, null);
        client.executeBatch(List.of(statement.bind(), statement.bind()), BatchType.UNLOGGED);
        client.executePage("SELECT * FROM t", 10, null);

        assertEquals(List.of(), client.idempotent);
    }

    @Test
    void theOptionMarksEachSafeStatementIdempotent() {
        FakeClient client = new FakeClient("write", "n=1", "-mode", "cql3", "native", "idempotent");
        StressPreparedStatement insert = client.prepare("INSERT INTO t (k) VALUES (?)");

        client.execute("SELECT * FROM t", ConsistencyLevel.ONE, null);
        client.executeCount("SELECT * FROM t", ConsistencyLevel.ONE, null);
        client.execute(insert.bind(), ConsistencyLevel.ONE, null);
        client.executeCount(insert.bind(), ConsistencyLevel.ONE, null);
        client.executeBatch(List.of(insert.bind()), BatchType.UNLOGGED);
        client.executeBatch(List.of(insert.bind(), insert.bind()), BatchType.UNLOGGED);
        client.executePage("SELECT * FROM t", 10, null);

        assertEquals(client.sent, client.idempotent);
    }

    @Test
    void theOptionLeavesSchemaLwtAndCounterStatementsNotIdempotent() {
        FakeClient client = new FakeClient("write", "n=1", "-mode", "cql3", "native", "idempotent");
        StressPreparedStatement lwt = client.prepare("INSERT INTO t (k) VALUES (?) IF NOT EXISTS");
        StressPreparedStatement counter = client.prepare("UPDATE c SET v = v + ? WHERE k = ?");

        client.execute("CREATE TABLE IF NOT EXISTS t (k int PRIMARY KEY)", ConsistencyLevel.ONE);
        client.execute("CREATE TABLE t2 (k int PRIMARY KEY)", ConsistencyLevel.ONE);
        client.execute(lwt.bind(), ConsistencyLevel.ONE, ConsistencyLevel.SERIAL);
        client.execute(counter.bind(), ConsistencyLevel.ONE, null);
        client.executeBatch(List.of(counter.bind()), BatchType.COUNTER);

        assertEquals(List.of(), client.idempotent);
    }

    @Test
    void aPreparedStatementClassifiesItsQueryOnce() {
        FakeClient client = new FakeClient("write", "n=1");

        assertTrue(client.prepare("INSERT INTO t (k) VALUES (?)").isIdempotent());
        assertFalse(client.prepare("UPDATE c SET v = v + ? WHERE k = ?").isIdempotent());
    }

    @Test
    void aPreparedStatementThatBindsAListIsNotIdempotent() {
        AbstractPreparedStatement statement =
                new AbstractPreparedStatement("UPDATE t SET l = ? WHERE k = ?", List.of("l", "k"), true) {
                    @Override
                    public StressBoundStatement bind(Object... values) {
                        return () -> this;
                    }
                };

        assertFalse(statement.isIdempotent());
    }

    @Test
    void aBatchIsIdempotentOnlyWhenEveryStatementIs() {
        FakeClient client = new FakeClient("write", "n=1", "-mode", "cql3", "native", "idempotent");
        StressPreparedStatement insert = client.prepare("INSERT INTO t (k) VALUES (?)");
        StressPreparedStatement append = client.prepare("UPDATE t SET l = l + ? WHERE k = ?");
        StressPreparedStatement set = client.prepare("UPDATE c SET v = ? WHERE k = ?");

        client.executeBatch(List.of(insert.bind(), append.bind()), BatchType.LOGGED);
        client.executeBatch(List.of(insert.bind(), set.bind()), BatchType.COUNTER);

        assertEquals(List.of(), client.idempotent);
    }

    @Test
    void aBatchTakesTheCommandConsistencyWhenItsStatementsHaveNone() {
        FakeClient client = new FakeClient("write", "n=1", "cl=QUORUM", "serial-cl=LOCAL_SERIAL");
        StressPreparedStatement statement = client.prepare("INSERT");

        client.executeBatch(List.of(statement.bind(), statement.bind()), BatchType.UNLOGGED);
        client.executeBatch(List.of(statement.bind()), BatchType.UNLOGGED);

        assertEquals(
                new Sent(
                        "BATCH UNLOGGED",
                        ConsistencyLevel.QUORUM,
                        ConsistencyLevel.LOCAL_SERIAL,
                        List.of(new Sent("INSERT", null, null, List.of()), new Sent("INSERT", null, null, List.of()))),
                client.sent.get(0));
        assertEquals(
                new Sent("INSERT", ConsistencyLevel.QUORUM, ConsistencyLevel.LOCAL_SERIAL, List.of()),
                client.sent.get(1));
    }

    @Test
    void aSingleStatementBatchTakesTheLevelsOfItsStatement() {
        FakeClient client = new FakeClient("write", "n=1", "cl=QUORUM", "serial-cl=LOCAL_SERIAL");
        StressPreparedStatement statement = client.prepare("INSERT");
        statement.setConsistencyLevel(ConsistencyLevel.ALL);
        statement.setSerialConsistencyLevel(ConsistencyLevel.SERIAL);

        client.executeBatch(List.of(statement.bind()), BatchType.UNLOGGED);

        assertEquals(
                List.of(new Sent("INSERT", ConsistencyLevel.ALL, ConsistencyLevel.SERIAL, List.of())), client.sent);
    }

    @Test
    void aBoundStatementKeepsItsOwnLevelsOverTheLevelsOfTheCall() {
        FakeClient client = new FakeClient("read", "n=1");
        StressPreparedStatement own = client.prepare("SELECT own");
        own.setConsistencyLevel(ConsistencyLevel.ALL);
        own.setSerialConsistencyLevel(ConsistencyLevel.SERIAL);
        StressPreparedStatement none = client.prepare("SELECT none");

        client.execute(own.bind(), ConsistencyLevel.ONE, ConsistencyLevel.LOCAL_SERIAL);
        client.executeCount(none.bind(), ConsistencyLevel.ONE, ConsistencyLevel.LOCAL_SERIAL);

        assertEquals(
                List.of(
                        new Sent("SELECT own", ConsistencyLevel.ALL, ConsistencyLevel.SERIAL, List.of()),
                        new Sent("SELECT none", ConsistencyLevel.ONE, ConsistencyLevel.LOCAL_SERIAL, List.of())),
                client.sent);
    }

    @Test
    void translatesDriverErrorsWhileFetchingLaterRows() {
        FakeClient client = new FakeClient("read", "n=1");
        client.rows = List.of("a", "b", "c");
        client.rowsBeforeFetchFailure = 2;
        StressPreparedStatement statement = client.prepare("SELECT");

        assertThrows(OverloadedException.class, () -> client.execute("SELECT", ConsistencyLevel.ONE, null));
        assertThrows(OverloadedException.class, () -> client.executeCount("SELECT", ConsistencyLevel.ONE, null));
        assertThrows(OverloadedException.class, () -> client.execute(statement.bind(), ConsistencyLevel.ONE, null));
        assertThrows(
                OverloadedException.class, () -> client.executeCount(statement.bind(), ConsistencyLevel.ONE, null));
    }

    @Test
    void aBatchTakesTheConsistencyOfItsFirstStatement() {
        FakeClient client = new FakeClient("write", "n=1", "cl=QUORUM");
        StressPreparedStatement statement = client.prepare("INSERT");
        statement.setConsistencyLevel(ConsistencyLevel.ALL);

        client.executeBatch(List.of(statement.bind(), statement.bind()), BatchType.LOGGED);

        assertEquals(ConsistencyLevel.ALL, client.sent.getFirst().consistency());
        assertEquals("BATCH LOGGED", client.sent.getFirst().cql());
        assertEquals(2, client.sent.getFirst().batch().size());
    }

    @Test
    void countsRowsWithoutReadingTheirValues() {
        FakeClient client = new FakeClient("read", "n=1");
        client.rows = List.of("a", "b", "c");

        assertEquals(3, client.executeCount("SELECT", ConsistencyLevel.ONE, null));
        assertEquals(0, client.valuesRead.get());
        assertEquals(
                3, client.execute("SELECT", ConsistencyLevel.ONE, null).rows().size());
        assertEquals(3, client.valuesRead.get());
    }

    @Test
    void preparesEachQueryOnceAndWrapsItAgainForEachCaller() {
        FakeClient client = new FakeClient("read", "n=1");

        assertNotSame(client.prepare("SELECT"), client.prepare("SELECT"));
        assertEquals(1, client.prepared.get());
    }

    @Test
    void translatesDriverErrors() {
        FakeClient client = new FakeClient("read", "n=1");
        client.failure = new IllegalStateException("busy");

        assertThrows(OverloadedException.class, () -> client.execute("SELECT", ConsistencyLevel.ONE, null));
    }

    @Test
    void unwrapsTheRingThatTheDriverReports() {
        assertEquals(
                List.of(new TokenSlice(Long.MIN_VALUE, 5), new TokenSlice(5, Long.MIN_VALUE)),
                new FakeClient("read", "n=1").tokenRanges());
    }
}
