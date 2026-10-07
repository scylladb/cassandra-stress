package org.apache.cassandra.stress.operations.userdefined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.cassandra.stress.WorkManager;
import org.apache.cassandra.stress.driver.AbstractPreparedStatement;
import org.apache.cassandra.stress.driver.BatchType;
import org.apache.cassandra.stress.driver.StressBoundStatement;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.generate.PartitionGenerator;
import org.apache.cassandra.stress.generate.SeedManager;
import org.apache.cassandra.stress.generate.values.GeneratorConfig;
import org.apache.cassandra.stress.generate.values.Integers;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.OptionDistribution;
import org.apache.cassandra.stress.settings.OptionRatioDistribution;
import org.apache.cassandra.stress.settings.StressSettings;
import org.junit.jupiter.api.Test;

class SchemaInsertTest {
    private static final StressSettings SETTINGS =
            StressSettings.parse(new String[] {"write", "n=1", "-errors", "retries=3"});

    record Bound(FailingStatement statement, Object[] values) implements StressBoundStatement {}

    private static final class FailingStatement extends AbstractPreparedStatement {
        final AtomicInteger bindFailures;

        FailingStatement(int bindFailures) {
            super("INSERT INTO t (pk, ck) VALUES (?, ?)", List.of("pk", "ck"));
            this.bindFailures = new AtomicInteger(bindFailures);
        }

        @Override
        public StressBoundStatement bind(Object... values) {
            if (bindFailures.getAndDecrement() > 0) {
                throw new IllegalStateException("bind failed");
            }
            return new Bound(this, values.clone());
        }
    }

    private static GeneratorConfig config(String name, String clustering) {
        return new GeneratorConfig(name, clustering == null ? null : OptionDistribution.get(clustering), null, null);
    }

    private static SchemaInsert insert(FailingStatement statement) {
        PartitionGenerator generator = new PartitionGenerator(
                List.of(new Integers("pk", config("pk", null))),
                List.of(new Integers("ck", config("ck", "fixed(10)"))),
                List.of(),
                PartitionGenerator.Order.ARBITRARY);
        SchemaInsert insert = new SchemaInsert(
                new Timer("insert", (opType, intended, started, ended, rows, partitions, error) -> {}),
                SETTINGS,
                generator,
                new SeedManager(SETTINGS),
                OptionDistribution.get("fixed(1)").get(),
                OptionRatioDistribution.BUILDER.apply("fixed(1)/1").get(),
                OptionRatioDistribution.BUILDER.apply("fixed(1)/1").get(),
                statement,
                BatchType.UNLOGGED);
        insert.maxBatchSize = 3;
        return insert;
    }

    private static StressClient client(List<List<Object[]>> sent, int failingBatch) {
        AtomicInteger calls = new AtomicInteger();
        return (StressClient) Proxy.newProxyInstance(
                SchemaInsertTest.class.getClassLoader(), new Class<?>[] {StressClient.class}, (proxy, method, args) -> {
                    if (!"executeBatch".equals(method.getName())) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    if (calls.incrementAndGet() == failingBatch) {
                        throw new IllegalStateException("timeout");
                    }
                    List<Object[]> values = new ArrayList<>();
                    for (Object statement : (List<?>) args[0]) {
                        values.add(((Bound) statement).values());
                    }
                    sent.add(values);
                    return null;
                });
    }

    private static List<Object> keys(List<List<Object[]>> sent) {
        return sent.stream()
                .flatMap(List::stream)
                .map(row -> List.of(row))
                .map(Object.class::cast)
                .toList();
    }

    @Test
    void aRetrySendsOnlyTheChunksThatFailed() throws Exception {
        SchemaInsert insert = insert(new FailingStatement(0));
        List<List<Object[]>> sent = new ArrayList<>();
        assertEquals(1, insert.ready(new WorkManager.FixedWorkManager(1)));

        insert.run(client(sent, 2));

        assertEquals(List.of(3, 3, 3, 1), sent.stream().map(List::size).toList());
        assertEquals(10, keys(sent).stream().distinct().count());
    }

    @Test
    void aRetryAfterAFailedBindWritesEveryRow() throws Exception {
        SchemaInsert insert = insert(new FailingStatement(1));
        List<List<Object[]>> sent = new ArrayList<>();
        assertEquals(1, insert.ready(new WorkManager.FixedWorkManager(1)));

        insert.run(client(sent, -1));

        assertEquals(10, keys(sent).size());
        assertEquals(10, keys(sent).stream().distinct().count());
    }
}
