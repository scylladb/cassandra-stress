// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder;
import com.datastax.oss.driver.api.core.loadbalancing.NodeDistance;
import com.datastax.oss.driver.api.core.loadbalancing.NodeDistanceEvaluator;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.TokenMap;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.ssl.ProgrammaticSslEngineFactory;
import com.datastax.oss.driver.api.core.ssl.SslEngineFactory;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.MutableCodecRegistry;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.internal.core.type.codec.registry.CodecRegistryConstants;
import com.datastax.oss.driver.internal.core.type.codec.registry.DefaultCodecRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import org.apache.cassandra.stress.core.PreparedStatement;
import org.apache.cassandra.stress.settings.LoadBalanceType;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.SettingsMode;
import org.apache.cassandra.stress.settings.SettingsNode;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.codecs.EpochDayCodec;
import org.apache.cassandra.stress.util.codecs.NanoOfDayCodec;
import org.apache.cassandra.stress.util.codecs.TimestampCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaDriverClient implements QueryExecutor, QueryPrepare, MetadataProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaDriverClient.class);

    public final List<HostAndPort> contactPoints;
    public final Integer maxPendingPerConnection;
    public final int connectionsPerHost;
    public final int requestTimeout;

    private final ResultLogger output;
    private final SettingsMode mode;
    private final SettingsNode node;
    private final ProtocolVersion protocolVersion;
    private final EncryptionOptions encryptionOptions;
    private final ConcurrentMap<String, PreparedStatement> statements = new ConcurrentHashMap<>();
    private volatile CqlSession session;

    public JavaDriverClient(StressSettings settings, List<String> hosts, int port) {
        this(settings, hosts, port, new EncryptionOptions());
    }

    public JavaDriverClient(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        this.contactPoints =
                hosts.stream().map(host -> HostAndPort.parse(host, port)).toList();
        this.output = settings.output();
        this.mode = settings.mode;
        this.node = settings.node;
        this.protocolVersion = settings.mode.protocolVersion.toDriver();
        this.encryptionOptions = encryptionOptions;
        this.connectionsPerHost = Objects.requireNonNullElse(settings.mode.connectionsPerHost, 8);
        this.requestTimeout = Objects.requireNonNullElse(settings.mode.requestTimeout, 12000);
        this.maxPendingPerConnection = settings.mode.maxPendingPerConnection;
    }

    @Override
    public PreparedStatement prepare(String query) {
        return statements.computeIfAbsent(
                query, q -> new PreparedStatement(getSession().prepare(q)));
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
        if (protocolVersion != null)
            config = config.withString(DefaultDriverOption.PROTOCOL_VERSION, protocolVersion.name());
        if (maxPendingPerConnection != null)
            config = config.withInt(DefaultDriverOption.CONNECTION_MAX_REQUESTS, maxPendingPerConnection);
        config = LoadBalanceType.of(node).applyTo(config, node);
        return compression.applyTo(config);
    }

    public void connect(ProtocolCompression compression) throws IOException {
        CqlSessionBuilder builder = CqlSession.builder()
                .addContactPoints(
                        contactPoints.stream().map(HostAndPort::toSocketAddress).toList())
                .withConfigLoader(config(compression).build())
                .withCodecRegistry(codecRegistry());

        if (node.isWhiteList) builder = builder.withNodeDistanceEvaluator(new WhiteList(contactPoints));
        if (encryptionOptions.enabled) builder = builder.withSslEngineFactory(sslEngineFactory());
        if (mode.authProvider.isSet()) builder = mode.authProvider.applyTo(builder);
        else if (mode.username != null) builder = builder.withCredentials(mode.username, mode.password);

        session = builder.build();

        Metadata metadata = session.getMetadata();
        output.printf(
                "Connected to cluster: %s, max pending requests per connection %d, max connections per host %d%n",
                metadata.getClusterName().orElse(null), maxPendingPerConnection, connectionsPerHost);
        for (Node host : metadata.getNodes().values()) {
            output.printf(
                    "Datatacenter: %s; Host: %s; Rack: %s%n",
                    host.getDatacenter(), host.getBroadcastRpcAddress().orElse(null), host.getRack());
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
            this(contactPoints.stream().map(WhiteList::resolve).collect(Collectors.toUnmodifiableSet()));
        }

        private static InetAddress resolve(HostAndPort contactPoint) {
            InetAddress address = contactPoint.toSocketAddress().getAddress();
            if (address == null)
                throw new IllegalArgumentException("Cannot resolve the whitelisted node " + contactPoint.host());
            return address;
        }

        @Override
        public NodeDistance evaluateDistance(Node node, String localDc) {
            return allows(node) ? null : NodeDistance.IGNORED;
        }

        boolean allows(Node node) {
            SocketAddress endpoint = node.getEndPoint().resolve();
            if (endpoint instanceof InetSocketAddress socket && addresses.contains(socket.getAddress())) return true;
            return node.getBroadcastRpcAddress()
                    .map(InetSocketAddress::getAddress)
                    .filter(addresses::contains)
                    .isPresent();
        }
    }

    public CqlSession getSession() {
        return session;
    }

    public Optional<TokenMap> getTokenMap() {
        return session.getMetadata().getTokenMap();
    }

    @Override
    public void execute(String query, ConsistencyLevel consistency) {
        ResultSet result = session.execute(new SimpleStatementBuilder(query)
                .setConsistencyLevel(consistency.toDriver())
                .build());
        if (!result.getExecutionInfo().isSchemaInAgreement())
            LOGGER.warn(
                    "No schema agreement from live replicas after {} s. The schema may not be up to date on some"
                            + " nodes.",
                    schemaAgreementWait().toSeconds());
    }

    private Duration schemaAgreementWait() {
        return session.getContext()
                .getConfig()
                .getDefaultProfile()
                .getDuration(DefaultDriverOption.CONTROL_CONNECTION_AGREEMENT_TIMEOUT);
    }

    public ResultSet execute(String query, ConsistencyLevel consistency, ConsistencyLevel serialConsistency) {
        SimpleStatementBuilder builder = new SimpleStatementBuilder(query);
        if (consistency != null) builder.setConsistencyLevel(consistency.toDriver());
        if (serialConsistency != null) builder.setSerialConsistencyLevel(serialConsistency.toDriver());
        return session.execute(builder.build());
    }

    public ResultSet executePrepared(
            PreparedStatement statement,
            List<Object> values,
            ConsistencyLevel consistency,
            ConsistencyLevel serialConsistency) {
        BoundStatement bound = statement.bind(values.toArray());
        if (statement.getConsistencyLevel() == null && consistency != null)
            bound = bound.setConsistencyLevel(consistency.toDriver());
        if (statement.getSerialConsistencyLevel() == null && serialConsistency != null)
            bound = bound.setSerialConsistencyLevel(serialConsistency.toDriver());
        return session.execute(bound);
    }

    @Override
    public TableMetadata getTableMetadata(String keyspace, String tableName) {
        return session.getMetadata()
                .getKeyspace(keyspace)
                .flatMap(ks -> ks.getTable(tableName))
                .orElse(null);
    }

    public void disconnect() {
        try {
            session.close();
        } catch (RuntimeException e) {
            output.printf("Failed to close connection due to the following error: %s%n", e);
        }
    }
}
