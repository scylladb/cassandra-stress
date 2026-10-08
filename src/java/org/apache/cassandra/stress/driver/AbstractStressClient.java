// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.cassandra.stress.settings.SettingsMode;
import org.apache.cassandra.stress.settings.SettingsNode;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.ResultLogger;

public abstract class AbstractStressClient<P, S, R, W> implements StressClient {
    protected final List<HostAndPort> contactPoints;
    protected final Integer maxPendingPerConnection;
    protected final int connectionsPerHost;
    protected final int requestTimeout;
    protected final ResultLogger output;
    protected final SettingsMode mode;
    protected final SettingsNode node;
    protected final EncryptionOptions encryptionOptions;
    protected final ConsistencyLevel defaultConsistency;
    protected final ConsistencyLevel defaultSerialConsistency;
    private final ConcurrentMap<String, P> statements = new ConcurrentHashMap<>();

    protected AbstractStressClient(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        this.contactPoints =
                hosts.stream().map(host -> HostAndPort.parse(host, port)).toList();
        this.output = settings.output();
        this.mode = settings.mode;
        this.node = settings.node;
        this.encryptionOptions = encryptionOptions;
        this.connectionsPerHost = Objects.requireNonNullElse(settings.mode.connectionsPerHost, 8);
        this.requestTimeout = Objects.requireNonNullElse(settings.mode.requestTimeout, 12000);
        this.maxPendingPerConnection = settings.mode.maxPendingPerConnection;
        this.defaultConsistency = settings.command.consistencyLevel;
        this.defaultSerialConsistency = settings.command.serialConsistencyLevel;
    }

    protected abstract P prepareInDriver(String query);

    protected abstract StressPreparedStatement wrap(P statement);

    protected abstract S simple(String query, ConsistencyLevel consistency, ConsistencyLevel serialConsistency);

    protected abstract S page(String query, int pageSize, Object pagingState);

    protected abstract S bound(
            StressBoundStatement statement, ConsistencyLevel consistency, ConsistencyLevel serialConsistency);

    protected abstract S batch(
            BatchType type, ConsistencyLevel consistency, ConsistencyLevel serialConsistency, List<S> statements);

    protected abstract R run(S statement);

    protected abstract RuntimeException translate(RuntimeException error);

    protected abstract Iterator<W> rows(R results);

    protected abstract int availableWithoutFetching(R results);

    protected abstract List<String> columnNames(R results);

    protected abstract ByteBuffer[] values(W row);

    protected abstract Object pagingState(R results);

    protected abstract boolean fullyFetched(R results);

    protected abstract List<TokenSlice> ringRanges();

    protected abstract void close();

    protected void afterSchemaStatement(R results) {}

    protected final void printConnected(String clusterName) {
        output.printf(
                "Connected to cluster: %s, max pending requests per connection %d, max connections per host %d%n",
                clusterName, maxPendingPerConnection, connectionsPerHost);
    }

    protected final void printHost(String datacenter, Object address, String rack) {
        output.printf("Datatacenter: %s; Host: %s; Rack: %s%n", datacenter, address, rack);
    }

    private R execute(S statement) {
        try {
            return run(statement);
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public StressPreparedStatement prepare(String query) {
        return wrap(statements.computeIfAbsent(query, this::prepareInDriver));
    }

    @Override
    public void execute(String query, ConsistencyLevel consistency) {
        afterSchemaStatement(execute(simple(query, consistency, null)));
    }

    @Override
    public StressResult execute(String query, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        return result(execute(simple(query, consistency, serialConsistency)));
    }

    @Override
    public StressResult execute(
            StressBoundStatement statement, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        return result(execute(bound(statement, consistency, serialConsistency)));
    }

    @Override
    public int executeCount(String query, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        return count(execute(simple(query, consistency, serialConsistency)));
    }

    @Override
    public int executeCount(
            StressBoundStatement statement, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        return count(execute(bound(statement, consistency, serialConsistency)));
    }

    @Override
    public void executeBatch(List<StressBoundStatement> statements, BatchType type) {
        if (statements.size() == 1) {
            execute(bound(statements.getFirst(), defaultConsistency, defaultSerialConsistency));
            return;
        }
        StressPreparedStatement first = statements.getFirst().statement();
        ConsistencyLevel consistency = Objects.requireNonNullElse(first.getConsistencyLevel(), defaultConsistency);
        ConsistencyLevel serialConsistency =
                Objects.requireNonNullElse(first.getSerialConsistencyLevel(), defaultSerialConsistency);
        List<S> bound = new ArrayList<>(statements.size());
        for (StressBoundStatement statement : statements) {
            bound.add(bound(statement, null, null));
        }
        execute(batch(type, consistency, serialConsistency, bound));
    }

    @Override
    public StressPage executePage(String query, int pageSize, Object pagingState) {
        R results = execute(page(query, pageSize, pagingState));
        int available = availableWithoutFetching(results);
        List<ByteBuffer[]> rows = new ArrayList<>(available);
        Iterator<W> iterator = rows(results);
        for (int i = 0; i < available; i++) {
            rows.add(values(iterator.next()));
        }
        return new StressPage(
                new StressResult(columnNames(results), rows), pagingState(results), fullyFetched(results));
    }

    private StressResult result(R results) {
        List<ByteBuffer[]> rows = new ArrayList<>();
        for (Iterator<W> iterator = rows(results); iterator.hasNext(); ) {
            rows.add(values(iterator.next()));
        }
        return new StressResult(columnNames(results), rows);
    }

    private int count(R results) {
        int count = 0;
        for (Iterator<W> iterator = rows(results); iterator.hasNext(); iterator.next()) {
            count++;
        }
        return count;
    }

    @Override
    public List<TokenSlice> tokenRanges() {
        return TokenSlice.sortedAndUnwrapped(ringRanges());
    }

    @Override
    public void disconnect() {
        try {
            close();
        } catch (RuntimeException e) {
            output.printf("Failed to close connection due to the following error: %s%n", e);
        }
    }
}
