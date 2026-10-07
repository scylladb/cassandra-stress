// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver.v4;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchableStatement;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.loadbalancing.NodeDistance;
import com.datastax.oss.driver.api.core.loadbalancing.NodeDistanceEvaluator;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.TokenMap;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.metadata.token.Token;
import com.datastax.oss.driver.api.core.metadata.token.TokenRange;
import com.datastax.oss.driver.api.core.servererrors.AlreadyExistsException;
import com.datastax.oss.driver.api.core.ssl.ProgrammaticSslEngineFactory;
import com.datastax.oss.driver.api.core.ssl.SslEngineFactory;
import com.datastax.oss.driver.api.core.type.CustomType;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.ListType;
import com.datastax.oss.driver.api.core.type.MapType;
import com.datastax.oss.driver.api.core.type.SetType;
import com.datastax.oss.driver.api.core.type.TupleType;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.MutableCodecRegistry;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token;
import com.datastax.oss.driver.internal.core.type.codec.registry.CodecRegistryConstants;
import com.datastax.oss.driver.internal.core.type.codec.registry.DefaultCodecRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
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
import org.apache.cassandra.stress.driver.v4.codecs.EpochDayCodec;
import org.apache.cassandra.stress.driver.v4.codecs.NanoOfDayCodec;
import org.apache.cassandra.stress.driver.v4.codecs.TimestampCodec;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.ConsistencyLevel;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.SSLFactory;
import org.apache.cassandra.stress.util.WhiteListAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JavaDriverV4Client
        extends AbstractStressClient<
                com.datastax.oss.driver.api.core.cql.PreparedStatement, Statement<?>, ResultSet, Row> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaDriverV4Client.class);

    private final ProtocolVersion protocolVersion;
    private volatile CqlSession session;

    public JavaDriverV4Client(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        super(settings, hosts, port, encryptionOptions);
        this.protocolVersion = V4DriverConfig.protocolVersion(settings.mode.protocolVersion);
    }

    static MutableCodecRegistry codecRegistry() {
        MutableCodecRegistry registry = new DefaultCodecRegistry(
                "cassandraStressCodecRegistry",
                Arrays.stream(CodecRegistryConstants.PRIMITIVE_CODECS)
                        .map(codec -> Objects.equals(codec, TypeCodecs.TIMESTAMP) ? new TimestampCodec() : codec)
                        .toArray(TypeCodec<?>[]::new));
        registry.register(new EpochDayCodec(), new NanoOfDayCodec());
        return registry;
    }

    ProgrammaticDriverConfigLoaderBuilder config(ProtocolCompression compression) {
        ProgrammaticDriverConfigLoaderBuilder config = new DefaultProgrammaticDriverConfigLoaderBuilder()
                .withString(DefaultDriverOption.LOAD_BALANCING_POLICY_CLASS, "DcInferringLoadBalancingPolicy")
                .withInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE, connectionsPerHost)
                .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofMillis(requestTimeout));
        if (protocolVersion != null) {
            config = config.withString(DefaultDriverOption.PROTOCOL_VERSION, protocolVersion.name());
        }
        if (maxPendingPerConnection != null) {
            config = config.withInt(DefaultDriverOption.CONNECTION_MAX_REQUESTS, maxPendingPerConnection);
        }
        config = V4DriverConfig.loadBalancing(config, node);
        return V4DriverConfig.compression(config, compression);
    }

    @Override
    public void connect(ProtocolCompression compression) throws IOException {
        CqlSessionBuilder builder = CqlSession.builder()
                .addContactPoints(
                        contactPoints.stream().map(HostAndPort::toSocketAddress).toList())
                .withConfigLoader(config(compression).build())
                .withCodecRegistry(codecRegistry());

        if (node.isWhiteList) {
            builder = builder.withNodeDistanceEvaluator(new WhiteList(contactPoints));
        }
        if (encryptionOptions.enabled) {
            builder = builder.withSslEngineFactory(sslEngineFactory());
        }
        builder = V4DriverConfig.auth(builder, mode.authProvider, mode.username, mode.password);

        session = builder.build();

        Metadata metadata = session.getMetadata();
        printConnected(metadata.getClusterName().orElse(null));
        for (Node host : metadata.getNodes().values()) {
            printHost(host.getDatacenter(), host.getBroadcastRpcAddress().orElse(null), host.getRack());
        }
    }

    @SuppressWarnings("PMD.CloseResource")
    private SslEngineFactory sslEngineFactory() throws IOException {
        SSLContext sslContext = SSLFactory.createSSLContext(encryptionOptions, true);
        ProgrammaticSslEngineFactory factory =
                new ProgrammaticSslEngineFactory(sslContext, encryptionOptions.cipherSuites);
        boolean verifyHostname = encryptionOptions.hostnameVerification;
        return new SslEngineFactory() {
            @Override
            public SSLEngine newSslEngine(EndPoint remoteEndpoint) {
                SSLEngine engine = factory.newSslEngine(remoteEndpoint);
                if (verifyHostname) {
                    SSLParameters parameters = engine.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    engine.setSSLParameters(parameters);
                }
                return engine;
            }

            @Override
            public void close() {
                factory.close();
            }
        };
    }

    record WhiteList(Set<InetAddress> addresses) implements NodeDistanceEvaluator {
        WhiteList(List<HostAndPort> contactPoints) {
            this(WhiteListAddresses.resolve(contactPoints));
        }

        @Override
        public NodeDistance evaluateDistance(Node node, String localDc) {
            return allows(node) ? null : NodeDistance.IGNORED;
        }

        boolean allows(Node node) {
            SocketAddress endpoint = node.getEndPoint().resolve();
            if (endpoint instanceof InetSocketAddress socket && addresses.contains(socket.getAddress())) {
                return true;
            }
            return node.getBroadcastRpcAddress()
                    .map(InetSocketAddress::getAddress)
                    .filter(addresses::contains)
                    .isPresent();
        }
    }

    public CqlSession getSession() {
        return session;
    }

    @Override
    protected com.datastax.oss.driver.api.core.cql.PreparedStatement prepareInDriver(String query) {
        return session.prepare(query);
    }

    @Override
    protected StressPreparedStatement wrap(com.datastax.oss.driver.api.core.cql.PreparedStatement statement) {
        return new V4PreparedStatement(statement);
    }

    @Override
    protected Statement<?> simple(String query, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        SimpleStatementBuilder builder = new SimpleStatementBuilder(query);
        if (consistency != null) {
            builder.setConsistencyLevel(V4DriverConfig.consistency(consistency));
        }
        if (serialConsistency != null) {
            builder.setSerialConsistencyLevel(V4DriverConfig.consistency(serialConsistency));
        }
        return builder.build();
    }

    @Override
    protected Statement<?> page(String query, int pageSize, Object pagingState) {
        SimpleStatementBuilder builder = new SimpleStatementBuilder(query).setPageSize(pageSize);
        if (pagingState != null) {
            builder.setPagingState((ByteBuffer) pagingState);
        }
        return builder.build();
    }

    @Override
    protected Statement<?> bound(
            StressBoundStatement statement, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        if (statement instanceof V4BoundStatement bound) {
            return bound.toDriver(consistency, serialConsistency);
        }
        throw new IllegalArgumentException("Driver 4.x cannot run a statement bound by another driver: " + statement);
    }

    @Override
    protected Statement<?> batch(
            BatchType type,
            ConsistencyLevel consistency,
            ConsistencyLevel serialConsistency,
            List<Statement<?>> statements) {
        BatchStatementBuilder batch = new BatchStatementBuilder(DefaultBatchType.valueOf(type.name()));
        if (consistency != null) {
            batch.setConsistencyLevel(V4DriverConfig.consistency(consistency));
        }
        if (serialConsistency != null) {
            batch.setSerialConsistencyLevel(V4DriverConfig.consistency(serialConsistency));
        }
        for (Statement<?> statement : statements) {
            batch.addStatement((BatchableStatement<?>) statement);
        }
        return batch.build();
    }

    @Override
    protected ResultSet run(Statement<?> statement) {
        return session.execute(statement);
    }

    @Override
    protected RuntimeException translate(RuntimeException error) {
        return switch (error) {
            case com.datastax.oss.driver.api.core.servererrors.OverloadedException e ->
                new OverloadedException(e.getMessage(), e);
            case AlreadyExistsException e -> new SchemaAlreadyExistsException(e.getMessage(), e);
            default -> error;
        };
    }

    @Override
    protected void afterSchemaStatement(ResultSet results) {
        if (!results.getExecutionInfo().isSchemaInAgreement()) {
            LOGGER.warn(
                    "No schema agreement from live replicas after {} s. The schema may not be up to date on some"
                            + " nodes.",
                    schemaAgreementWait().toSeconds());
        }
    }

    private Duration schemaAgreementWait() {
        return session.getContext()
                .getConfig()
                .getDefaultProfile()
                .getDuration(DefaultDriverOption.CONTROL_CONNECTION_AGREEMENT_TIMEOUT);
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
        for (ColumnDefinition definition : definitions) {
            names.add(definition.getName().asInternal());
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
        return session.getMetadata()
                .getKeyspace(keyspace)
                .flatMap(ks -> ks.getTable(tableName))
                .map(JavaDriverV4Client::schema)
                .orElse(null);
    }

    static TableSchema schema(TableMetadata table) {
        List<ColumnSchema> partitionKey = new ArrayList<>();
        for (ColumnMetadata column : table.getPartitionKey()) {
            partitionKey.add(column(column, false));
        }
        List<ColumnSchema> clustering = new ArrayList<>();
        table.getClusteringColumns()
                .forEach((column, order) -> clustering.add(column(column, order == ClusteringOrder.DESC)));
        List<ColumnMetadata> key = table.getPrimaryKey();
        List<ColumnSchema> values = new ArrayList<>();
        for (ColumnMetadata column : table.getColumns().values()) {
            if (!key.contains(column)) {
                values.add(column(column, false));
            }
        }
        return new TableSchema(
                table.getKeyspace().asInternal(), table.getName().asInternal(), partitionKey, clustering, values);
    }

    private static ColumnSchema column(ColumnMetadata column, boolean descending) {
        return new ColumnSchema(column.getName().asInternal(), type(column.getType()), descending);
    }

    static CqlType type(DataType type) {
        return switch (type) {
            case ListType list -> new CqlType("LIST", List.of(type(list.getElementType())), list.isFrozen());
            case SetType set -> new CqlType("SET", List.of(type(set.getElementType())), set.isFrozen());
            case MapType map ->
                new CqlType("MAP", List.of(type(map.getKeyType()), type(map.getValueType())), map.isFrozen());
            case UserDefinedType udt -> new CqlType("UDT", List.of(), udt.isFrozen());
            default -> CqlType.of(name(type));
        };
    }

    private static String name(DataType type) {
        if (type instanceof TupleType) {
            return "TUPLE";
        }
        if (type instanceof CustomType) {
            return "CUSTOM";
        }
        return type.asCql(false, true).toUpperCase(Locale.ROOT);
    }

    @Override
    protected List<TokenSlice> ringRanges() {
        TokenMap tokenMap = session.getMetadata()
                .getTokenMap()
                .orElseThrow(() -> new IllegalStateException("The driver has no token map"));
        List<TokenSlice> ranges = new ArrayList<>();
        for (TokenRange range : tokenMap.getTokenRanges()) {
            ranges.add(new TokenSlice(token(range.getStart()), token(range.getEnd())));
        }
        return ranges;
    }

    static long token(Token token) {
        if (token instanceof Murmur3Token murmur3) {
            return murmur3.getValue();
        }
        throw new IllegalStateException("Only the Murmur3 partitioner is supported, got " + token);
    }

    @Override
    protected void close() {
        session.close();
    }
}
