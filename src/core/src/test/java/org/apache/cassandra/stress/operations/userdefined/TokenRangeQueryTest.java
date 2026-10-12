package org.apache.cassandra.stress.operations.userdefined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.cassandra.stress.StressYaml;
import org.apache.cassandra.stress.WorkManager;
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.CqlType;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressPage;
import org.apache.cassandra.stress.driver.StressResult;
import org.apache.cassandra.stress.driver.TableSchema;
import org.apache.cassandra.stress.driver.TokenSlice;
import org.apache.cassandra.stress.generate.TokenRangeIterator;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.junit.jupiter.api.Test;

class TokenRangeQueryTest {
    private static final TableSchema TABLE =
            new TableSchema("ks", "t", List.of(new ColumnSchema("pk", CqlType.of("BIGINT"))), List.of(), List.of());

    private static StressClient client(List<String> queries, StressPage... pages) {
        Map<String, Integer> next = new HashMap<>(Map.of("page", 0));
        return (StressClient) Proxy.newProxyInstance(
                TokenRangeQueryTest.class.getClassLoader(),
                new Class<?>[] {StressClient.class},
                (proxy, method, args) -> {
                    if ("executePage".equals(method.getName())) {
                        queries.add((String) args[0]);
                        return pages[next.merge("page", 1, Integer::sum) - 1];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static TokenRangeQuery query(StressSettings settings, TokenSlice range) {
        StressYaml.TokenRangeQueryDef def = new StressYaml.TokenRangeQueryDef();
        def.columns = "pk";
        return new TokenRangeQuery(
                new Timer("scan", (opType, intended, started, ended, rowCount, partitions, error) -> {}),
                settings,
                TABLE,
                new TokenRangeIterator(settings, List.of(range)),
                def,
                false);
    }

    private static ByteBuffer token(long value) {
        return ByteBuffer.allocate(8).putLong(0, value);
    }

    @Test
    void readsTheRangeAndKeepsThePagingStateUntilTheLastPage() throws Exception {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-errors", "retries=0"});
        List<String> queries = new ArrayList<>();
        StressResult first = new StressResult(
                List.of("system.token(pk)", "pk"), List.<ByteBuffer[]>of(new ByteBuffer[] {token(1), token(1)}));
        StressClient client = client(
                queries,
                new StressPage(first, "state", false),
                new StressPage(new StressResult(List.of("system.token(pk)", "pk"), List.of()), null, true));
        TokenRangeQuery query = query(settings, new TokenSlice(-5, 7));

        assertEquals(1, query.ready(new WorkManager.FixedWorkManager(2)));
        query.run(client);
        assertEquals("[-5, 7]", query.key());
        query.run(client);
        assertEquals("-", query.key());
        assertEquals(
                List.of(
                        "SELECT token(pk), pk FROM t WHERE token(pk) > -5 AND token(pk) <= 7",
                        "SELECT token(pk), pk FROM t WHERE token(pk) > -5 AND token(pk) <= 7"),
                queries);
    }

    @Test
    void anEmptyPageWithAPagingStateKeepsTheRangeOpen() throws Exception {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-errors", "retries=0"});
        StressClient client =
                client(new ArrayList<>(), new StressPage(new StressResult(List.of(), List.of()), "state", false));
        TokenRangeQuery query = query(settings, new TokenSlice(0, 0));

        assertEquals(1, query.ready(new WorkManager.FixedWorkManager(1)));
        query.run(client);
        assertEquals("[0, 0]", query.key());
    }

    @Test
    void readsToTheEndOfTheRingWithoutAnUpperBound() throws Exception {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=1", "-errors", "retries=0"});
        List<String> queries = new ArrayList<>();
        StressClient client = client(
                queries, new StressPage(new StressResult(List.of("system.token(pk)", "pk"), List.of()), null, true));
        TokenRangeQuery query = query(settings, new TokenSlice(100, Long.MIN_VALUE));

        assertEquals(1, query.ready(new WorkManager.FixedWorkManager(1)));
        query.run(client);
        assertEquals(List.of("SELECT token(pk), pk FROM t WHERE token(pk) > 100"), queries);
    }
}
