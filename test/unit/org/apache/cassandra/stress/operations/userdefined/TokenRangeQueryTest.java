package org.apache.cassandra.stress.operations.userdefined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.TokenMap;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.token.Token;
import com.datastax.oss.driver.api.core.metadata.token.TokenRange;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.internal.core.metadata.schema.DefaultColumnMetadata;
import com.datastax.oss.driver.internal.core.metadata.schema.DefaultTableMetadata;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.cassandra.stress.StressYaml;
import org.apache.cassandra.stress.WorkManager;
import org.apache.cassandra.stress.generate.TokenRangeIterator;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.JavaDriverClient;
import org.junit.jupiter.api.Test;

class TokenRangeQueryTest {
    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> answers) {
        return (T) Proxy.newProxyInstance(
                TokenRangeQueryTest.class.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
                    if (answers.containsKey(method.getName())) return answers.get(method.getName());
                    if ("toString".equals(method.getName())) return type.getSimpleName();
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
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
        ResultSet page = fake(
                ResultSet.class,
                Map.of(
                        "getExecutionInfo",
                        fake(ExecutionInfo.class, Map.of("getPagingState", ByteBuffer.allocate(4))),
                        "getAvailableWithoutFetching",
                        0,
                        "iterator",
                        rows,
                        "isFullyFetched",
                        false));
        Token token = fake(Token.class, Map.of());
        TokenMap tokenMap = fake(TokenMap.class, Map.of("format", "0"));
        CqlSession session = fake(
                CqlSession.class,
                Map.of(
                        "execute",
                        page,
                        "getMetadata",
                        fake(Metadata.class, Map.of("getTokenMap", Optional.of(tokenMap)))));

        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-errors", "retries=0"});
        JavaDriverClient client = new JavaDriverClient(settings, List.of("127.0.0.1"), 9042);
        Field field = JavaDriverClient.class.getDeclaredField("session");
        field.setAccessible(true);
        field.set(client, session);

        CqlIdentifier keyspace = CqlIdentifier.fromInternal("ks");
        CqlIdentifier table = CqlIdentifier.fromInternal("t");
        ColumnMetadata pk =
                new DefaultColumnMetadata(keyspace, table, CqlIdentifier.fromInternal("pk"), DataTypes.BIGINT, false);
        StressYaml.TokenRangeQueryDef def = new StressYaml.TokenRangeQueryDef();
        def.columns = "pk";
        TokenRange range = fake(TokenRange.class, Map.of("getStart", token, "getEnd", token));
        TokenRangeQuery query = new TokenRangeQuery(
                new Timer("scan", (opType, intended, started, ended, rowCount, partitions, error) -> {}),
                settings,
                new DefaultTableMetadata(
                        keyspace,
                        table,
                        UUID.randomUUID(),
                        false,
                        false,
                        List.of(pk),
                        Map.<ColumnMetadata, ClusteringOrder>of(),
                        Map.of(pk.getName(), pk),
                        Map.of(),
                        Map.of()),
                new TokenRangeIterator(settings, Set.of(range)),
                def,
                false);

        assertEquals(1, query.ready(new WorkManager.FixedWorkManager(1)));
        query.run(client);
        assertEquals(0, rowsRead.get());
        assertEquals("[0, 0]", query.key());
    }
}
