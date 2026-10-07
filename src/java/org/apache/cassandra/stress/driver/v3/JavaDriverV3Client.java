// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v3;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ClusteringOrder;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.ColumnMetadata;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.PagingState;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.RemoteEndpointAwareJdkSSLOptions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.TableMetadata;
import com.datastax.driver.core.TokenRange;
import com.datastax.driver.core.exceptions.AlreadyExistsException;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.shaded.netty.channel.socket.SocketChannel;
import com.datastax.shaded.netty.util.internal.logging.InternalLoggerFactory;
import com.datastax.shaded.netty.util.internal.logging.Slf4JLoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import org.apache.cassandra.stress.driver.BatchType;
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.CqlType;
import org.apache.cassandra.stress.driver.OverloadedException;
import org.apache.cassandra.stress.driver.SchemaAlreadyExistsException;
import org.apache.cassandra.stress.driver.StressBoundStatement;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.driver.StressPage;
import org.apache.cassandra.stress.driver.StressPreparedStatement;
import org.apache.cassandra.stress.driver.StressResult;
import org.apache.cassandra.stress.driver.TableSchema;
import org.apache.cassandra.stress.driver.TokenSlice;
import org.apache.cassandra.stress.driver.v3.codecs.EpochDayCodec;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.SettingsMode;
import org.apache.cassandra.stress.settings.SettingsNode;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.ResultLogger;
import org.apache.cassandra.stress.util.SSLFactory;

public final class JavaDriverV3Client implements StressClient {
    static {
        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
    }

    private final List<HostAndPort> contactPoints;
    private final Integer maxPendingPerConnection;
    private final int connectionsPerHost;
    private final int requestTimeout;
    private final ResultLogger output;
    private final SettingsMode mode;
    private final SettingsNode node;
    private final ProtocolVersion protocolVersion;
    private final EncryptionOptions encryptionOptions;
    private final ConcurrentMap<String, V3PreparedStatement> statements = new ConcurrentHashMap<>();
    private volatile Cluster cluster;
    private volatile Session session;

    public JavaDriverV3Client(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        this.contactPoints =
                hosts.stream().map(host -> HostAndPort.parse(host, port)).toList();
        this.output = settings.output();
        this.mode = settings.mode;
        this.node = settings.node;
        this.protocolVersion = V3DriverConfig.protocolVersion(settings.mode.protocolVersion);
        this.encryptionOptions = encryptionOptions;
        this.connectionsPerHost = Objects.requireNonNullElse(settings.mode.connectionsPerHost, 8);
        this.requestTimeout = Objects.requireNonNullElse(settings.mode.requestTimeout, 12000);
        this.maxPendingPerConnection = settings.mode.maxPendingPerConnection;
    }

    public static String driverVersion() {
        return Cluster.getDriverVersion();
    }

    static CodecRegistry codecRegistry() {
        return new CodecRegistry().register(new EpochDayCodec());
    }

    Cluster.Builder builder(ProtocolCompression compression) throws IOException {
        PoolingOptions pooling = new PoolingOptions()
                .setConnectionsPerHost(HostDistance.LOCAL, connectionsPerHost, connectionsPerHost)
                .setNewConnectionThreshold(HostDistance.LOCAL, 100);
        if (maxPendingPerConnection != null)
            pooling.setMaxRequestsPerConnection(HostDistance.LOCAL, maxPendingPerConnection);

        Cluster.Builder builder = Cluster.builder()
                .addContactPointsWithPorts(
                        contactPoints.stream().map(HostAndPort::toSocketAddress).toList())
                .withPoolingOptions(pooling)
                .withoutJMXReporting()
                .withoutMetrics()
                .withSocketOptions(new SocketOptions().setReadTimeoutMillis(requestTimeout))
                .withLoadBalancingPolicy(V3DriverConfig.loadBalancing(node, contactPoints))
                .withCompression(V3DriverConfig.compression(compression))
                .withCodecRegistry(codecRegistry());
        if (protocolVersion != null) builder.withProtocolVersion(protocolVersion);
        if (encryptionOptions.enabled) builder.withSSL(sslOptions());
        return V3DriverConfig.auth(builder, mode.authProvider, mode.username, mode.password);
    }

    @Override
    public void connect(ProtocolCompression compression) throws IOException {
        cluster = builder(compression).build();
        try {
            Metadata metadata = cluster.getMetadata();
            output.printf(
                    "Connected to cluster: %s, max pending requests per connection %d, max connections per host %d%n",
                    metadata.getClusterName(), maxPendingPerConnection, connectionsPerHost);
            for (Host host : metadata.getAllHosts()) {
                output.printf(
                        "Datatacenter: %s; Host: %s; Rack: %s%n",
                        host.getDatacenter(), host.getEndPoint().resolve(), host.getRack());
            }
            session = cluster.connect();
        } catch (NoHostAvailableException e) {
            Throwable handshake = find(e, SSLHandshakeException.class);
            if (handshake != null)
                output.printf(
                        "  Failed to connect to node due to an error during SSL handshake %s: %s%n",
                        handshake.getClass().getName(), handshake.getMessage());
            throw e;
        }
    }

    private static Throwable find(NoHostAvailableException e, Class<? extends Throwable> type) {
        for (Throwable error : e.getErrors().values()) {
            for (Throwable current = error; current != null; current = current.getCause())
                if (type.isInstance(current)) return current;
        }
        return null;
    }

    private RemoteEndpointAwareJdkSSLOptions sslOptions() throws IOException {
        SSLContext sslContext = SSLFactory.createSSLContext(encryptionOptions, true);
        boolean verifyHostname = encryptionOptions.hostnameVerification;
        return new RemoteEndpointAwareJdkSSLOptions(sslContext, encryptionOptions.cipherSuites) {
            @Override
            protected SSLEngine newSSLEngine(SocketChannel channel, InetSocketAddress remoteEndpoint) {
                SSLEngine engine = super.newSSLEngine(channel, remoteEndpoint);
                if (verifyHostname) {
                    SSLParameters parameters = engine.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    engine.setSSLParameters(parameters);
                }
                return engine;
            }
        };
    }

    public Session getSession() {
        return session;
    }

    @Override
    public StressPreparedStatement prepare(String query) {
        return statements.computeIfAbsent(query, q -> new V3PreparedStatement(session.prepare(q)));
    }

    @Override
    public void execute(String query, ConsistencyLevel consistency) {
        SimpleStatement statement = new SimpleStatement(query);
        statement.setConsistencyLevel(V3DriverConfig.consistency(consistency));
        try {
            session.execute(statement);
        } catch (AlreadyExistsException e) {
            throw new SchemaAlreadyExistsException(e.getMessage(), e);
        }
    }

    @Override
    public StressResult execute(String query, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        SimpleStatement statement = new SimpleStatement(query);
        if (consistency != null) statement.setConsistencyLevel(V3DriverConfig.consistency(consistency));
        if (serialConsistency != null)
            statement.setSerialConsistencyLevel(V3DriverConfig.consistency(serialConsistency));
        return result(overloadAware(() -> session.execute(statement)));
    }

    @Override
    public StressResult execute(
            StressBoundStatement statement, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        return result(overloadAware(() -> session.execute(bound(statement).toDriver(consistency, serialConsistency))));
    }

    @Override
    public void executeBatch(List<StressBoundStatement> statements, BatchType type) {
        if (statements.size() == 1) {
            overloadAware(() -> session.execute(bound(statements.getFirst()).toDriver(null, null)));
            return;
        }
        BatchStatement batch = new BatchStatement(BatchStatement.Type.valueOf(type.name()));
        StressPreparedStatement first = statements.getFirst().statement();
        if (first.getConsistencyLevel() != null)
            batch.setConsistencyLevel(V3DriverConfig.consistency(first.getConsistencyLevel()));
        if (first.getSerialConsistencyLevel() != null)
            batch.setSerialConsistencyLevel(V3DriverConfig.consistency(first.getSerialConsistencyLevel()));
        for (StressBoundStatement statement : statements)
            batch.add(bound(statement).toDriver(null, null));
        overloadAware(() -> session.execute(batch));
    }

    private static V3BoundStatement bound(StressBoundStatement statement) {
        if (statement instanceof V3BoundStatement bound) return bound;
        throw new IllegalArgumentException("Driver 3.x cannot run a statement bound by another driver: " + statement);
    }

    @Override
    public StressPage executePage(String query, int pageSize, Object pagingState) {
        SimpleStatement statement = new SimpleStatement(query);
        statement.setFetchSize(pageSize);
        if (pagingState != null) statement.setPagingState((PagingState) pagingState);
        ResultSet results = overloadAware(() -> session.execute(statement));
        int available = results.getAvailableWithoutFetching();
        List<ByteBuffer[]> rows = new ArrayList<>(available);
        for (int i = 0; i < available; i++) rows.add(values(results.one()));
        return new StressPage(
                new StressResult(names(results.getColumnDefinitions()), rows),
                results.getExecutionInfo().getPagingState(),
                results.isFullyFetched());
    }

    static StressResult result(ResultSet results) {
        List<ByteBuffer[]> rows = new ArrayList<>();
        for (Row row : results) rows.add(values(row));
        return new StressResult(names(results.getColumnDefinitions()), rows);
    }

    private static List<String> names(ColumnDefinitions definitions) {
        List<String> names = new ArrayList<>(definitions.size());
        for (int i = 0; i < definitions.size(); i++) names.add(definitions.getName(i));
        return names;
    }

    private static ByteBuffer[] values(Row row) {
        ByteBuffer[] values = new ByteBuffer[row.getColumnDefinitions().size()];
        for (int i = 0; i < values.length; i++) values[i] = row.getBytesUnsafe(i);
        return values;
    }

    @Override
    public TableSchema tableSchema(String keyspace, String tableName) {
        KeyspaceMetadata metadata = cluster.getMetadata().getKeyspace(Metadata.quoteIfNecessary(keyspace));
        if (metadata == null) return null;
        TableMetadata table = metadata.getTable(Metadata.quoteIfNecessary(tableName));
        return table == null ? null : schema(table, descendingColumns(keyspace, tableName));
    }

    private Set<String> descendingColumns(String keyspace, String tableName) {
        SimpleStatement statement = new SimpleStatement(
                "SELECT column_name, clustering_order FROM system_schema.columns"
                        + " WHERE keyspace_name = ? AND table_name = ?",
                keyspace,
                tableName);
        Set<String> descending = new HashSet<>();
        for (Row row : session.execute(statement))
            if (isDescending(row.getString("clustering_order"))) descending.add(row.getString("column_name"));
        return descending;
    }

    static boolean isDescending(String clusteringOrder) {
        return "desc".equalsIgnoreCase(clusteringOrder);
    }

    static TableSchema schema(TableMetadata table, Set<String> descendingColumns) {
        List<ColumnSchema> partitionKey = new ArrayList<>();
        for (ColumnMetadata column : table.getPartitionKey()) partitionKey.add(column(column, false));
        List<ColumnSchema> clustering = new ArrayList<>();
        List<ClusteringOrder> orders = table.getClusteringOrder();
        List<ColumnMetadata> clusteringColumns = table.getClusteringColumns();
        for (int i = 0; i < clusteringColumns.size(); i++) {
            ColumnMetadata column = clusteringColumns.get(i);
            boolean descending = orders.get(i) == ClusteringOrder.DESC || descendingColumns.contains(column.getName());
            clustering.add(column(column, descending));
        }
        List<ColumnMetadata> key = table.getPrimaryKey();
        List<ColumnSchema> values = new ArrayList<>();
        for (ColumnMetadata column : table.getColumns()) if (!key.contains(column)) values.add(column(column, false));
        return new TableSchema(table.getKeyspace().getName(), table.getName(), partitionKey, clustering, values);
    }

    private static ColumnSchema column(ColumnMetadata column, boolean descending) {
        return new ColumnSchema(column.getName(), type(column.getType()), descending);
    }

    static CqlType type(DataType type) {
        DataType.Name name = type.getName();
        return switch (name) {
            case LIST, SET, MAP ->
                new CqlType(
                        name.name(),
                        type.getTypeArguments().stream()
                                .map(JavaDriverV3Client::type)
                                .toList(),
                        type.isFrozen());
            case UDT -> new CqlType("UDT", List.of(), type.isFrozen());
            case VARCHAR -> CqlType.of("TEXT");
            default -> CqlType.of(name.name().toUpperCase(Locale.ROOT));
        };
    }

    @Override
    public List<TokenSlice> tokenRanges() {
        List<TokenSlice> ranges = new ArrayList<>();
        for (TokenRange range : cluster.getMetadata().getTokenRanges())
            ranges.add(new TokenSlice(token(range.getStart()), token(range.getEnd())));
        return TokenSlice.sortedAndUnwrapped(ranges);
    }

    static long token(com.datastax.driver.core.Token token) {
        if (token.getValue() instanceof Long value) return value;
        throw new IllegalStateException("Only the Murmur3 partitioner is supported, got " + token);
    }

    private static <T> T overloadAware(Supplier<T> call) {
        try {
            return call.get();
        } catch (com.datastax.driver.core.exceptions.OverloadedException e) {
            throw new OverloadedException(e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        try {
            cluster.close();
        } catch (RuntimeException e) {
            output.printf("Failed to close connection due to the following error: %s%n", e);
        }
    }
}
