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
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.shaded.netty.channel.socket.SocketChannel;
import com.datastax.shaded.netty.util.internal.logging.InternalLoggerFactory;
import com.datastax.shaded.netty.util.internal.logging.Slf4JLoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import org.apache.cassandra.stress.driver.AbstractStressClient;
import org.apache.cassandra.stress.driver.BatchType;
import org.apache.cassandra.stress.driver.ColumnSchema;
import org.apache.cassandra.stress.driver.CqlType;
import org.apache.cassandra.stress.driver.OverloadedException;
import org.apache.cassandra.stress.driver.SchemaAlreadyExistsException;
import org.apache.cassandra.stress.driver.StressBoundStatement;
import org.apache.cassandra.stress.driver.StressPreparedStatement;
import org.apache.cassandra.stress.driver.TableSchema;
import org.apache.cassandra.stress.driver.TokenSlice;
import org.apache.cassandra.stress.driver.v3.codecs.EpochDayCodec;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JavaDriverV3Client
        extends AbstractStressClient<
                com.datastax.driver.core.PreparedStatement, com.datastax.driver.core.Statement, ResultSet, Row> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaDriverV3Client.class);

    static {
        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
    }

    private final ProtocolVersion protocolVersion;
    private volatile Cluster cluster;
    private volatile Session session;

    public JavaDriverV3Client(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        super(settings, hosts, port, encryptionOptions);
        this.protocolVersion = V3DriverConfig.protocolVersion(settings.mode.protocolVersion);
    }

    static CodecRegistry codecRegistry() {
        return new CodecRegistry().register(new EpochDayCodec());
    }

    Cluster.Builder builder(ProtocolCompression compression) throws IOException {
        PoolingOptions pooling = new PoolingOptions()
                .setConnectionsPerHost(HostDistance.LOCAL, connectionsPerHost, connectionsPerHost)
                .setNewConnectionThreshold(HostDistance.LOCAL, 100);
        if (maxPendingPerConnection != null) {
            pooling.setMaxRequestsPerConnection(HostDistance.LOCAL, maxPendingPerConnection);
        }

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
        if (protocolVersion != null) {
            builder.withProtocolVersion(protocolVersion);
        }
        if (encryptionOptions.enabled) {
            builder.withSSL(sslOptions());
        }
        return V3DriverConfig.auth(builder, mode.authProvider, mode.username, mode.password);
    }

    @Override
    public void connect(ProtocolCompression compression) throws IOException {
        cluster = builder(compression).build();
        try {
            Metadata metadata = cluster.getMetadata();
            printConnected(metadata.getClusterName());
            for (Host host : metadata.getAllHosts()) {
                printHost(host.getDatacenter(), host.getEndPoint().resolve(), host.getRack());
            }
            session = cluster.connect();
        } catch (NoHostAvailableException e) {
            Throwable handshake = find(e, SSLHandshakeException.class);
            if (handshake != null) {
                output.printf(
                        "  Failed to connect to node due to an error during SSL handshake %s: %s%n",
                        handshake.getClass().getName(), handshake.getMessage());
            }
            throw e;
        }
    }

    private static Throwable find(NoHostAvailableException e, Class<? extends Throwable> type) {
        for (Throwable error : e.getErrors().values()) {
            for (Throwable current = error; current != null; current = current.getCause()) {
                if (type.isInstance(current)) {
                    return current;
                }
            }
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
    protected com.datastax.driver.core.PreparedStatement prepareInDriver(String query) {
        return session.prepare(query);
    }

    @Override
    protected StressPreparedStatement wrap(com.datastax.driver.core.PreparedStatement statement) {
        return new V3PreparedStatement(statement);
    }

    @Override
    protected com.datastax.driver.core.Statement simple(
            String query, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        SimpleStatement statement = new SimpleStatement(query);
        if (consistency != null) {
            statement.setConsistencyLevel(V3DriverConfig.consistency(consistency));
        }
        if (serialConsistency != null) {
            statement.setSerialConsistencyLevel(V3DriverConfig.consistency(serialConsistency));
        }
        return statement;
    }

    @Override
    protected com.datastax.driver.core.Statement page(String query, int pageSize, Object pagingState) {
        SimpleStatement statement = new SimpleStatement(query);
        statement.setFetchSize(pageSize);
        if (pagingState != null) {
            statement.setPagingState((PagingState) pagingState);
        }
        return statement;
    }

    @Override
    protected com.datastax.driver.core.Statement bound(
            StressBoundStatement statement, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        if (statement instanceof V3BoundStatement bound) {
            return bound.toDriver(consistency, serialConsistency);
        }
        throw new IllegalArgumentException("Driver 3.x cannot run a statement bound by another driver: " + statement);
    }

    @Override
    protected com.datastax.driver.core.Statement batch(
            BatchType type,
            ConsistencyLevel consistency,
            ConsistencyLevel serialConsistency,
            List<com.datastax.driver.core.Statement> statements) {
        BatchStatement batch = new BatchStatement(BatchStatement.Type.valueOf(type.name()));
        if (consistency != null) {
            batch.setConsistencyLevel(V3DriverConfig.consistency(consistency));
        }
        if (serialConsistency != null) {
            batch.setSerialConsistencyLevel(V3DriverConfig.consistency(serialConsistency));
        }
        batch.addAll(statements);
        return batch;
    }

    @Override
    protected ResultSet run(com.datastax.driver.core.Statement statement) {
        return session.execute(statement);
    }

    @Override
    protected RuntimeException translate(RuntimeException error) {
        return switch (error) {
            case com.datastax.driver.core.exceptions.OverloadedException e ->
                new OverloadedException(e.getMessage(), e);
            case AlreadyExistsException e -> new SchemaAlreadyExistsException(e.getMessage(), e);
            default -> error;
        };
    }

    @Override
    protected Iterator<Row> rows(ResultSet results) {
        return results.iterator();
    }

    @Override
    protected int availableWithoutFetching(ResultSet results) {
        return results.getAvailableWithoutFetching();
    }

    @Override
    protected List<String> columnNames(ResultSet results) {
        ColumnDefinitions definitions = results.getColumnDefinitions();
        List<String> names = new ArrayList<>(definitions.size());
        for (int i = 0; i < definitions.size(); i++) {
            names.add(definitions.getName(i));
        }
        return names;
    }

    @Override
    protected ByteBuffer[] values(Row row) {
        ByteBuffer[] values = new ByteBuffer[row.getColumnDefinitions().size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = row.getBytesUnsafe(i);
        }
        return values;
    }

    @Override
    protected Object pagingState(ResultSet results) {
        return results.getExecutionInfo().getPagingState();
    }

    @Override
    protected boolean fullyFetched(ResultSet results) {
        return results.isFullyFetched();
    }

    @Override
    public TableSchema tableSchema(String keyspace, String tableName) {
        KeyspaceMetadata metadata = cluster.getMetadata().getKeyspace(keyspace);
        if (metadata == null) {
            return null;
        }
        TableMetadata table = metadata.getTable(tableName);
        return table == null ? null : schema(table, descendingColumns(metadata.getName(), table.getName()));
    }

    private Set<String> descendingColumns(String keyspace, String tableName) {
        SimpleStatement statement = new SimpleStatement(
                "SELECT column_name, clustering_order FROM system_schema.columns"
                        + " WHERE keyspace_name = ? AND table_name = ?",
                keyspace,
                tableName);
        Set<String> descending = new HashSet<>();
        try {
            for (Row row : session.execute(statement)) {
                if (isDescending(row.getString("clustering_order"))) {
                    descending.add(row.getString("column_name"));
                }
            }
        } catch (DriverException e) {
            LOGGER.warn(
                    "Could not read the clustering order of {}.{} from system_schema.columns, so the driver"
                            + " metadata sets it: {}",
                    keyspace,
                    tableName,
                    e.getMessage());
        }
        return descending;
    }

    static boolean isDescending(String clusteringOrder) {
        return "desc".equalsIgnoreCase(clusteringOrder);
    }

    static TableSchema schema(TableMetadata table, Set<String> descendingColumns) {
        List<ColumnSchema> partitionKey = new ArrayList<>();
        for (ColumnMetadata column : table.getPartitionKey()) {
            partitionKey.add(column(column, false));
        }
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
        for (ColumnMetadata column : table.getColumns()) {
            if (!key.contains(column)) {
                values.add(column(column, false));
            }
        }
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
    protected List<TokenSlice> ringRanges() {
        List<TokenSlice> ranges = new ArrayList<>();
        for (TokenRange range : cluster.getMetadata().getTokenRanges()) {
            ranges.add(new TokenSlice(token(range.getStart()), token(range.getEnd())));
        }
        return ranges;
    }

    static long token(com.datastax.driver.core.Token token) {
        if (token.getValue() instanceof Long value) {
            return value;
        }
        throw new IllegalStateException("Only the Murmur3 partitioner is supported, got " + token);
    }

    @Override
    protected void close() {
        cluster.close();
    }
}
