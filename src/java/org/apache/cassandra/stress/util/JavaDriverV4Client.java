/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.stress.util;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.apache.cassandra.config.EncryptionOptions;
import org.apache.cassandra.security.SSLFactory;
import org.apache.cassandra.stress.core.PreparedStatement;
import org.apache.cassandra.stress.core.TableMetadata;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.codecs.TimestampCodec;
import shaded.com.datastax.oss.driver.api.core.AllNodesFailedException;
import shaded.com.datastax.oss.driver.api.core.CqlSession;
import shaded.com.datastax.oss.driver.api.core.CqlSessionBuilder;
import shaded.com.datastax.oss.driver.api.core.ProtocolVersion;
import shaded.com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import shaded.com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import shaded.com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import shaded.com.datastax.oss.driver.api.core.cql.ResultSet;
import shaded.com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder;
import shaded.com.datastax.oss.driver.api.core.metadata.EndPoint;
import shaded.com.datastax.oss.driver.api.core.metadata.Metadata;
import shaded.com.datastax.oss.driver.api.core.metadata.Node;
import shaded.com.datastax.oss.driver.api.core.ssl.ProgrammaticSslEngineFactory;
import shaded.com.datastax.oss.driver.api.core.ssl.SslEngineFactory;
import shaded.com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import shaded.com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import shaded.com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import shaded.com.datastax.oss.driver.api.core.type.codec.registry.MutableCodecRegistry;
import shaded.com.datastax.oss.driver.internal.core.config.typesafe.DefaultProgrammaticDriverConfigLoaderBuilder;
import shaded.com.datastax.oss.driver.internal.core.type.codec.registry.CodecRegistryConstants;
import shaded.com.datastax.oss.driver.internal.core.type.codec.registry.DefaultCodecRegistry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class JavaDriverV4Client implements QueryExecutor, QueryPrepare, MetadataProvider
{

    static
    {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
    }

    public final List<String> hosts;
    public final int port;
    public final String username;
    public final String password;
    public final JavaDriverV4SessionBuilder authProvider;
    public final Integer maxPendingPerConnection;
    public final int connectionsPerHost;

    private final ProtocolVersion protocolVersion;
    private final EncryptionOptions.ClientEncryptionOptions encryptionOptions;
    private CqlSession session;
    private final JavaDriverV4ConfigBuilder loadBalancingPolicy;
    private final File cloudConfigFile;


    private static final ConcurrentMap<String, PreparedStatement> stmts = new ConcurrentHashMap<>();

    public JavaDriverV4Client(StressSettings settings, List<String> hosts, int port)
    {
        this(settings, hosts, port, new EncryptionOptions.ClientEncryptionOptions());
    }

    public JavaDriverV4Client(StressSettings settings, List<String> hosts, int port, EncryptionOptions.ClientEncryptionOptions encryptionOptions)
    {
        this.protocolVersion = settings.mode.protocolVersion.ToJavaDriverV4();
        this.hosts = hosts;
        this.port = port;
        this.username = settings.mode.username;
        this.password = settings.mode.password;
        this.authProvider = settings.mode.authProvider.ToJavaDriverV4();
        this.encryptionOptions = encryptionOptions;
        this.loadBalancingPolicy = loadBalancingPolicy(settings);
        this.connectionsPerHost = settings.mode.connectionsPerHost == null ? 8 : settings.mode.connectionsPerHost;
        this.cloudConfigFile = settings.cloudConfig.file;

        int maxThreadCount = 0;
        if (settings.rate.auto)
            maxThreadCount = settings.rate.maxThreads;
        else
            maxThreadCount = settings.rate.threadCount;

        //Always allow enough pending requests so every thread can have a request pending
        //See https://issues.apache.org/jira/browse/CASSANDRA-7217
        int requestsPerConnection = (maxThreadCount / connectionsPerHost) + connectionsPerHost;

        maxPendingPerConnection = settings.mode.maxPendingPerConnection;
    }

    private JavaDriverV4ConfigBuilder loadBalancingPolicy(StressSettings settings)
    {
        return new JavaDriverV4ConfigBuilder() {

            @Override
            public ProgrammaticDriverConfigLoaderBuilder applyConfig(ProgrammaticDriverConfigLoaderBuilder builder) {
                if (settings.node.rack != null) {
                    builder = builder.withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_RACK, settings.node.rack);
                }
                if (settings.node.datacenter != null) {
                    builder = builder.withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, settings.node.datacenter);
                }
                if (settings.node.isWhiteList)
                    throw new IllegalArgumentException("Whitelist policy is not supported by Driver 4.x");
                return builder;
            }
        };
    }

    public PreparedStatement prepare(String query)
    {
        PreparedStatement stmt = stmts.get(query);
        if (stmt != null)
            return stmt;
        synchronized (stmts)
        {
            stmt = stmts.get(query);
            if (stmt != null)
                return stmt;
            stmt = new PreparedStatement(getSession().prepare(query));
            stmts.put(query, stmt);
        }
        return stmt;
    }

    // prepareCodecRegistry creates custom codec registry replacing some codecs types to replicate 3.x driver behavior.
    public MutableCodecRegistry prepareCodecRegistry()
    {
        return new DefaultCodecRegistry(
            "cassandraCustomCodecRegistry",
            Arrays.stream(CodecRegistryConstants.PRIMITIVE_CODECS).map(c -> {
              if ((c == TypeCodecs.TIMESTAMP)) {
                // Default code converts TIMESTAMP to java.time.Instant, 3.x does it to java.time.Date
                // So we need to replace it
                return new TimestampCodec();
              }
              return c;
            }).collect(Collectors.toList()).toArray(TypeCodec<?>[]::new)
        ) {
        };
    }

    public void connect(ProtocolCompression compression) throws Exception
    {
        ProgrammaticDriverConfigLoaderBuilder configBuilder = new DefaultProgrammaticDriverConfigLoaderBuilder();
        CqlSessionBuilder sessionBuilder = CqlSession.builder();
        configBuilder.withInt(
            DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE, connectionsPerHost);

        if (protocolVersion != null) {
            configBuilder.withString(DefaultDriverOption.PROTOCOL_VERSION, protocolVersion.name());
        }

        if (maxPendingPerConnection != null)
        {
            configBuilder.withInt(
                DefaultDriverOption.CONNECTION_MAX_REQUESTS, maxPendingPerConnection);
        }

        if (this.cloudConfigFile == null)
        {
            sessionBuilder.addContactPoints(hosts.stream().map((h) -> {
                String[] chunks = h.split(":", 2);
                if (chunks.length == 2) {
                    return new InetSocketAddress(chunks[0], Integer.parseInt(chunks[1]));
                }
                return new InetSocketAddress(chunks[0], this.port);
            }).collect(Collectors.toList()));
        }

        if (loadBalancingPolicy != null)
        {
            configBuilder = loadBalancingPolicy.applyConfig(configBuilder);
        }

        compression.ToJavaDriverV4().applyConfig(configBuilder);

        if (encryptionOptions.enabled)
        {
            SSLContext sslContext;
            sslContext = SSLFactory.createSSLContext(encryptionOptions, true);


            SslEngineFactory sslOptions = new SslEngineFactory()
            {
                final ProgrammaticSslEngineFactory factory = new ProgrammaticSslEngineFactory(
                    sslContext, encryptionOptions.cipher_suites);

                @Override
                public void close() {}

                @Override
                public SSLEngine newSslEngine(EndPoint remoteEndpoint) {
                    SSLEngine engine = factory.newSslEngine(remoteEndpoint);
                    if (encryptionOptions.hostname_verification) {
                        SSLParameters parameters = engine.getSSLParameters();
                        parameters.setEndpointIdentificationAlgorithm("HTTPS");
                        engine.setSSLParameters(parameters);
                    }
                    return engine;
                }
            };

            sessionBuilder.withSslEngineFactory(sslOptions);
        }

        if (authProvider != null)
        {
            authProvider.apply(sessionBuilder);
        }
        else if (username != null)
        {
            sessionBuilder.withCredentials(username, password);
        }

        if (this.cloudConfigFile != null)
        {
            throw new RuntimeException("Option -cloudconf is not supported on driver 4.x");
        }

        sessionBuilder.withConfigLoader(configBuilder.build());
        try {
            session = sessionBuilder.withCodecRegistry(prepareCodecRegistry()).build();
            Metadata metadata = session.getMetadata();
            System.out.printf(
                    "Connected to cluster: %s, max pending requests per connection %d, max connections per host %d%n",
                    metadata.getClusterName(),
                    maxPendingPerConnection,
                    connectionsPerHost);
            Map<UUID, Node> nodes = metadata.getNodes();
            for (UUID hostUUID : nodes.keySet())
            {
                Node host = nodes.get(hostUUID);
                System.out.printf("Datatacenter: %s; Host: %s; Rack: %s%n",
                        host.getDatacenter(), host.getBroadcastRpcAddress(), host.getRack());
            }
        } catch (AllNodesFailedException e) {
            Throwable sslException = findExceptionInErrors(e, SSLHandshakeException.class);
            if (sslException != null)
                System.err.println(String.format(
                        "  Failed to connect to node due to an error during SSL handshake %s: %s",
                        sslException.getClass().getName(), sslException.getMessage()));
            throw e;
        }
    }

    private Throwable findExceptionInErrors(AllNodesFailedException e, Class<? extends Throwable> exceptionClass) {
        for (Throwable error : e.getErrors().values()) {
            Throwable current = error;
            while (current != null) {
                if (exceptionClass.isInstance(current)) {
                    return current;
                }
                current = current.getCause();
            }
        }
        return null;
    }

    public CqlSession getSession()
    {
        return session;
    }

    public void execute(String query, org.apache.cassandra.db.ConsistencyLevel consistency)
    {
        SimpleStatementBuilder builder = new SimpleStatementBuilder(query);
        builder.setConsistencyLevel(consistency.ToV4Value());
        session.execute(builder.build());
    }

    public ResultSet execute(String query, org.apache.cassandra.db.ConsistencyLevel consistency,
                             org.apache.cassandra.db.ConsistencyLevel serialConsistency)
    {
        SimpleStatementBuilder builder = new SimpleStatementBuilder(query);
        builder.setConsistencyLevel(consistency.ToV4Value());
        builder.setSerialConsistencyLevel(serialConsistency.ToV4Value());
        return getSession().execute(builder.build());
    }

    public ResultSet executePrepared(PreparedStatement stmt, List<Object> queryParams, org.apache.cassandra.db.ConsistencyLevel consistency)
    {
        BoundStatementBuilder builder = stmt.ToV4Value().boundStatementBuilder((Object[]) queryParams.toArray(new Object[queryParams.size()]));
        builder = builder.setConsistencyLevel(consistency.ToV4Value());
        return getSession().execute(builder.build());
    }

    public TableMetadata getTableMetadata(String keyspace, String tableName) {
        return new TableMetadata(getSession().getMetadata().getKeyspace(keyspace).flatMap( ks -> ks.getTable(tableName)).orElse(null));
    }

    public ResultSet executePrepared(PreparedStatement stmt, List<Object> queryParams, org.apache.cassandra.db.ConsistencyLevel consistency, org.apache.cassandra.db.ConsistencyLevel serialConsistency )
    {
        BoundStatementBuilder builder = stmt.ToV4Value().boundStatementBuilder((Object[]) queryParams.toArray(new Object[queryParams.size()]));
        builder = builder.setConsistencyLevel(consistency.ToV4Value());
        builder.setSerialConsistencyLevel(serialConsistency.ToV4Value());
        return getSession().execute(builder.build());
    }

    public void disconnect()
    {
        try {
            session.close();
        } catch (Exception e) {
            System.out.printf(
                    "Failed to close connection due to the following error: %s", e);
        }
    }
}
