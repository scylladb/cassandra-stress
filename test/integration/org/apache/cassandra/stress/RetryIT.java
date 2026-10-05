package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.JavaDriverClient;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RetryIT {
    @TempDir
    Path dir;

    private Path profile() throws IOException {
        return Files.writeString(dir.resolve("retry.yaml"), """
            keyspace: retry
            keyspace_definition: |
              CREATE KEYSPACE retry WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 1};
            table: rows
            table_definition: |
              CREATE TABLE rows (pk bigint, ck int, v text, PRIMARY KEY (pk, ck))
            columnspec:
              - name: ck
                cluster: fixed(3)
            insert:
              partitions: fixed(1)
              batchtype: UNLOGGED
            queries:
              samerow:
                cql: select * from rows where pk = ? and ck = ?
                fields: samerow
              multirow:
                cql: select * from rows where pk = ? and ck = ?
                fields: multirow
            """);
    }

    private static CqlSession failingEveryOtherWrite(CqlSession session) {
        AtomicLong calls = new AtomicLong();
        return (CqlSession) Proxy.newProxyInstance(
                RetryIT.class.getClassLoader(), new Class<?>[] {CqlSession.class}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())
                            && args != null
                            && (args[0] instanceof BoundStatement || args[0] instanceof BatchStatement)
                            && calls.incrementAndGet() % 2 == 1) throw new IllegalStateException("injected failure");
                    try {
                        return method.invoke(session, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private StressResult runWithFailingSession(String... command) throws Exception {
        List<String> args = new ArrayList<>(List.of(command));
        args.addAll(List.of(
                "-rate",
                "threads=1",
                "-errors",
                "retries=3",
                "fail-fast",
                "-node",
                ScyllaNode.host(),
                "datacenter=" + ScyllaNode.DATACENTER,
                "-port",
                "native=" + ScyllaNode.port()));
        StressSettings settings = StressSettings.parse(args.toArray(String[]::new));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MultiResultLogger output = new MultiResultLogger(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        settings.setOutput(output);

        JavaDriverClient client = new JavaDriverClient(
                settings, settings.node.nodes, settings.port.nativePort, settings.transport.getEncryptionOptions());
        client.connect(settings.mode.compression());
        Field session = JavaDriverClient.class.getDeclaredField("session");
        session.setAccessible(true);
        session.set(client, failingEveryOtherWrite((CqlSession) session.get(client)));
        Field cached = StressSettings.class.getDeclaredField("client");
        cached.setAccessible(true);
        cached.set(settings, client);

        int exitCode = 0;
        try {
            new StressAction(settings, output).run();
        } catch (RuntimeException e) {
            exitCode = 1;
        } finally {
            settings.disconnect();
        }
        return new StressResult(exitCode, bytes.toString(StandardCharsets.UTF_8));
    }

    private void insert(Path profile) throws Exception {
        StressResult insert = runWithFailingSession(
                "user", "profile=" + profile, "ops(insert=1)", "no-warmup", "n=100", "-pop", "seq=1..100");
        assertTrue(insert.succeeded(), insert::toString);
        assertEquals(0L, insert.totalErrors().orElseThrow(), insert::toString);
    }

    @Test
    void aRetriedInsertWritesEveryRow() throws Exception {
        ScyllaNode.dropKeyspace("retry");
        insert(profile());
        assertEquals(300, ScyllaNode.count("retry", "rows"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"samerow", "multirow", "validate"})
    void aRetriedReadCountsEveryOperationWithoutErrors(String operation) throws Exception {
        ScyllaNode.dropKeyspace("retry");
        Path profile = profile();
        insert(profile);

        StressResult read = runWithFailingSession(
                "user", "profile=" + profile, "ops(" + operation + "=1)", "no-warmup", "n=100", "-pop", "seq=1..100");
        assertTrue(read.succeeded(), read::toString);
        assertEquals(0L, read.totalErrors().orElseThrow(), read::toString);
        if (!"validate".equals(operation))
            assertEquals(100L, read.totalPartitions().orElseThrow(), read::toString);
    }
}
