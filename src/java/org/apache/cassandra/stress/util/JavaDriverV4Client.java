// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.ssl.ProgrammaticSslEngineFactory;
import com.datastax.oss.driver.api.core.ssl.SslEngineFactory;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.MutableCodecRegistry;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.internal.core.type.codec.registry.CodecRegistryConstants;
import com.datastax.oss.driver.internal.core.type.codec.registry.DefaultCodecRegistry;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import org.apache.cassandra.stress.core.PreparedStatement;
import org.apache.cassandra.stress.core.TableMetadata;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.codecs.TimestampCodec;

@SuppressWarnings("PMD.CloseResource")
public class JavaDriverV4Client implements QueryExecutor, QueryPrepare, MetadataProvider {

    public final List<String> hosts;
    public final int port;
    public final String username;
    public final String password;
    public final JavaDriverV4SessionBuilder authProvider;
    public final Integer maxPendingPerConnection;
    public final int connectionsPerHost;
    public final int requestTimeout;

    private final ProtocolVersion protocolVersion;
    private final EncryptionOptions encryptionOptions;
    private CqlSession session;
    private final JavaDriverV4ConfigBuilder loadBalancingPolicy;

    private final ConcurrentMap<String, PreparedStatement> stmts = new ConcurrentHashMap<>();

    public JavaDriverV4Client(StressSettings settings, List<String> hosts, int port) {
        this(settings, hosts, port, new EncryptionOptions());
    }

    public JavaDriverV4Client(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        this.protocolVersion = settings.mode.protocolVersion.toJavaDriverV4();
        this.hosts = hosts;
        this.port = port;
        this.username = settings.mode.username;
        this.password = settings.mode.password;
        this.authProvider = settings.mode.authProvider.toJavaDriverV4();
        this.encryptionOptions = encryptionOptions;
        this.loadBalancingPolicy = loadBalancingPolicy(settings);
        this.connectionsPerHost = Objects.requireNonNullElse(settings.mode.connectionsPerHost, 8);
        this.requestTimeout = Objects.requireNonNullElse(settings.mode.requestTimeout, 12000);

        maxPendingPerConnection = settings.mode.maxPendingPerConnection;
    }

    private JavaDriverV4ConfigBuilder loadBalancingPolicy(StressSettings settings) {
        return new JavaDriverV4ConfigBuilder() {

            @Override
            public ProgrammaticDriverConfigLoaderBuilder applyConfig(ProgrammaticDriverConfigLoaderBuilder builder) {
                if (settings.node.rack != null) {
                    builder = builder.withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_RACK, settings.node.rack);
                }

                if (settings.node.datacenter != null) {
                    builder = builder.withString(
                            DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, settings.node.datacenter);
                }

                if (settings.node.isWhiteList)
                    throw new IllegalArgumentException("Whitelist policy is not supported by Driver 4.x");
                return builder;
            }
        };
    }

    @Override
    public PreparedStatement prepare(String query) {
        return stmts.computeIfAbsent(
                query, q -> new PreparedStatement(getSession().prepare(q)));
    }

    public MutableCodecRegistry prepareCodecRegistry() {
        return new DefaultCodecRegistry(
                "cassandraCustomCodecRegistry",
                Arrays.stream(CodecRegistryConstants.PRIMITIVE_CODECS)
                        .map(c -> {
                            if (Objects.equals(c, TypeCodecs.TIMESTAMP)) {
                                return new TimestampCodec();
                            }
                            return c;
                        })
                        .toArray(TypeCodec<?>[]::new)) {};
    }

    public void connect(ProtocolCompression compression) throws Exception {
        try {
            connectInternal(compression);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("local DC must be explicitly set")) {
                String discoveredDc = discoverLocalDatacenter(compression);
                if (discoveredDc == null || discoveredDc.isBlank()) throw e;

                connectInternal(compression, discoveredDc);
                return;
            }
            throw e;
        }
    }

    private void connectInternal(ProtocolCompression compression) throws Exception {
        connectInternal(compression, null);
    }

    private void connectInternal(ProtocolCompression compression, String overrideLocalDc) throws Exception {
        ProgrammaticDriverConfigLoaderBuilder configBuilder = new DefaultProgrammaticDriverConfigLoaderBuilder();
        CqlSessionBuilder sessionBuilder = CqlSession.builder();
        configBuilder.withInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE, connectionsPerHost);

        configBuilder.withDuration(DefaultDriverOption.REQUEST_TIMEOUT, java.time.Duration.ofMillis(requestTimeout));

        if (protocolVersion != null)
            configBuilder.withString(DefaultDriverOption.PROTOCOL_VERSION, protocolVersion.name());

        if (maxPendingPerConnection != null)
            configBuilder.withInt(DefaultDriverOption.CONNECTION_MAX_REQUESTS, maxPendingPerConnection);

        sessionBuilder.addContactPoints(hosts.stream()
                .map((h) -> {
                    String[] chunks = h.split(":", 2);
                    if (chunks.length == 2) return new InetSocketAddress(chunks[0], Integer.parseInt(chunks[1]));
                    return new InetSocketAddress(chunks[0], this.port);
                })
                .toList());

        if (loadBalancingPolicy != null) configBuilder = loadBalancingPolicy.applyConfig(configBuilder);

        if (overrideLocalDc != null)
            configBuilder =
                    configBuilder.withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, overrideLocalDc);

        compression.toJavaDriverV4().applyConfig(configBuilder);

        if (encryptionOptions.enabled) {
            SSLContext sslContext = SSLFactory.createSSLContext(encryptionOptions, true);
            SslEngineFactory sslOptions = new SslEngineFactory() {
                final ProgrammaticSslEngineFactory factory =
                        new ProgrammaticSslEngineFactory(sslContext, encryptionOptions.cipherSuites);

                @Override
                public void close() {}

                @Override
                public SSLEngine newSslEngine(EndPoint remoteEndpoint) {
                    SSLEngine engine = factory.newSslEngine(remoteEndpoint);
                    if (encryptionOptions.hostnameVerification) {
                        SSLParameters parameters = engine.getSSLParameters();
                        parameters.setEndpointIdentificationAlgorithm("HTTPS");
                        engine.setSSLParameters(parameters);
                    }
                    return engine;
                }
            };

            sessionBuilder.withSslEngineFactory(sslOptions);
        }

        if (authProvider != null) authProvider.apply(sessionBuilder);
        else if (username != null) sessionBuilder.withCredentials(username, password);

        sessionBuilder.withConfigLoader(configBuilder.build());

        session = sessionBuilder.withCodecRegistry(prepareCodecRegistry()).build();

        Metadata metadata = session.getMetadata();
        System.out.printf(
                "Connected to cluster: %s, max pending requests per connection %d, max connections per host %d%n",
                metadata.getClusterName(), maxPendingPerConnection, connectionsPerHost);
        Map<UUID, Node> nodes = metadata.getNodes();
        for (Node host : nodes.values()) {
            System.out.printf(
                    "Datatacenter: %s; Host: %s; Rack: %s%n",
                    host.getDatacenter(), host.getBroadcastRpcAddress(), host.getRack());
        }
    }

    private String discoverLocalDatacenter(ProtocolCompression compression) throws Exception {
        ProgrammaticDriverConfigLoaderBuilder configBuilder = new DefaultProgrammaticDriverConfigLoaderBuilder();
        CqlSessionBuilder sessionBuilder = CqlSession.builder();

        configBuilder.withInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE, connectionsPerHost);

        configBuilder.withDuration(DefaultDriverOption.REQUEST_TIMEOUT, java.time.Duration.ofMillis(requestTimeout));

        if (protocolVersion != null)
            configBuilder.withString(DefaultDriverOption.PROTOCOL_VERSION, protocolVersion.name());
        if (maxPendingPerConnection != null)
            configBuilder.withInt(DefaultDriverOption.CONNECTION_MAX_REQUESTS, maxPendingPerConnection);

        sessionBuilder.addContactPoints(hosts.stream()
                .map((h) -> {
                    String[] chunks = h.split(":", 2);
                    if (chunks.length == 2) return new InetSocketAddress(chunks[0], Integer.parseInt(chunks[1]));
                    return new InetSocketAddress(chunks[0], this.port);
                })
                .toList());

        if (loadBalancingPolicy != null) {
            configBuilder = loadBalancingPolicy.applyConfig(configBuilder);
        }

        compression.toJavaDriverV4().applyConfig(configBuilder);

        if (encryptionOptions.enabled) {
            SSLContext sslContext = SSLFactory.createSSLContext(encryptionOptions, true);
            SslEngineFactory sslOptions = new SslEngineFactory() {
                final ProgrammaticSslEngineFactory factory =
                        new ProgrammaticSslEngineFactory(sslContext, encryptionOptions.cipherSuites);

                @Override
                public void close() {}

                @Override
                public SSLEngine newSslEngine(EndPoint remoteEndpoint) {
                    SSLEngine engine = factory.newSslEngine(remoteEndpoint);
                    if (encryptionOptions.hostnameVerification) {
                        SSLParameters parameters = engine.getSSLParameters();
                        parameters.setEndpointIdentificationAlgorithm("HTTPS");
                        engine.setSSLParameters(parameters);
                    }
                    return engine;
                }
            };

            sessionBuilder.withSslEngineFactory(sslOptions);
        }

        if (authProvider != null) authProvider.apply(sessionBuilder);
        else if (username != null) sessionBuilder.withCredentials(username, password);

        sessionBuilder.withConfigLoader(configBuilder.build());
        try (CqlSession tmp =
                sessionBuilder.withCodecRegistry(prepareCodecRegistry()).build()) {
            for (Node node : tmp.getMetadata().getNodes().values()) {
                if (node.getDatacenter() != null) return node.getDatacenter();
            }
            return null;
        }
    }

    public CqlSession getSession() {
        return session;
    }

    @Override
    public void execute(String query, org.apache.cassandra.stress.util.ConsistencyLevel consistency) {
        SimpleStatementBuilder builder = new SimpleStatementBuilder(query);
        builder.setConsistencyLevel(consistency.toV4Value());
        session.execute(builder.build());
    }

    public ResultSet execute(
            String query,
            org.apache.cassandra.stress.util.ConsistencyLevel consistency,
            org.apache.cassandra.stress.util.ConsistencyLevel serialConsistency) {
        SimpleStatementBuilder builder = new SimpleStatementBuilder(query);
        builder.setConsistencyLevel(consistency.toV4Value());
        builder.setSerialConsistencyLevel(serialConsistency.toV4Value());
        return getSession().execute(builder.build());
    }

    public ResultSet executePrepared(
            PreparedStatement stmt,
            List<Object> queryParams,
            org.apache.cassandra.stress.util.ConsistencyLevel consistency) {
        BoundStatementBuilder builder =
                stmt.toV4Value().boundStatementBuilder((Object[]) queryParams.toArray(new Object[0]));
        builder = builder.setConsistencyLevel(consistency.toV4Value());
        return getSession().execute(builder.build());
    }

    @Override
    public TableMetadata getTableMetadata(String keyspace, String tableName) {
        return getSession()
                .getMetadata()
                .getKeyspace(keyspace)
                .flatMap(ks -> ks.getTable(tableName))
                .map(table -> new TableMetadata(table))
                .orElse(null);
    }

    public ResultSet executePrepared(
            PreparedStatement stmt,
            List<Object> queryParams,
            org.apache.cassandra.stress.util.ConsistencyLevel consistency,
            org.apache.cassandra.stress.util.ConsistencyLevel serialConsistency) {
        BoundStatementBuilder builder =
                stmt.toV4Value().boundStatementBuilder((Object[]) queryParams.toArray(new Object[0]));
        builder = builder.setConsistencyLevel(consistency.toV4Value());
        builder.setSerialConsistencyLevel(serialConsistency.toV4Value());
        return getSession().execute(builder.build());
    }

    public void disconnect() {
        try {
            session.close();
        } catch (Exception e) {
            System.out.printf("Failed to close connection due to the following error: %s", e);
        }
    }
}
