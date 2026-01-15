/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.scylladb.stress.util;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;

import com.datastax.driver.core.*;
import com.datastax.driver.core.RemoteEndpointAwareJdkSSLOptions;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.scylladb.stress.settings.LoadBalanceType;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy.ReplicaOrdering;
import com.datastax.driver.core.policies.WhiteListPolicy;
import com.datastax.shaded.netty.channel.socket.SocketChannel;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import com.scylladb.utils.EncryptionOptions;
import org.apache.cassandra.security.SSLFactory;
import com.scylladb.stress.core.BoundStatement;
import com.scylladb.stress.core.PreparedStatement;
import com.scylladb.stress.core.TableMetadata;
import com.scylladb.stress.settings.ProtocolCompression;
import com.scylladb.stress.settings.StressSettings;

public class JavaDriverClient implements QueryExecutor, QueryPrepare, MetadataProvider {

    static {
        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.getDefaultFactory());
    }

    public final List<String> hosts;
    public final int port;
    public final String username;
    public final String password;
    public final AuthProvider authProvider;
    public final Integer maxPendingPerConnection;
    public final int connectionsPerHost;

    private final ProtocolVersion protocolVersion;
    private final EncryptionOptions.ClientEncryptionOptions encryptionOptions;
    private Cluster cluster;
    private Session session;
    private final LoadBalancingPolicy loadBalancingPolicy;


    private static final ConcurrentMap<String, PreparedStatement> stmts = new ConcurrentHashMap<>();

    public JavaDriverClient(StressSettings settings, List<String> hosts, int port, EncryptionOptions.ClientEncryptionOptions encryptionOptions) {
        this.protocolVersion = settings.mode().protocolVersion.ToJavaDriverV3();
        this.hosts = hosts;
        this.port = port;
        this.username = settings.mode().username;
        this.password = settings.mode().password;
        this.authProvider = settings.mode().authProvider.ToJavaDriverV3();
        this.encryptionOptions = encryptionOptions;
        this.loadBalancingPolicy = loadBalancingPolicy(settings);
        this.connectionsPerHost = settings.mode().connectionsPerHost == null ? 8 : settings.mode().connectionsPerHost;

        int maxThreadCount = 0;
        if (settings.rate().auto)
            maxThreadCount = settings.rate().maxThreads;
        else
            maxThreadCount = settings.rate().threadCount;

        //Always allow enough pending requests so every thread can have a request pending
        //See https://issues.apache.org/jira/browse/CASSANDRA-7217
        int requestsPerConnection = (maxThreadCount / connectionsPerHost) + connectionsPerHost;

        maxPendingPerConnection = settings.mode().maxPendingPerConnection;
    }

    private LoadBalancingPolicy loadBalancingPolicy(StressSettings settings) {
        LoadBalancingPolicy ret;

        // Check if loadbalance option is specified
        if (settings.node().loadBalance != null) {
            ret = settings.node().loadBalance.createPolicy(settings);
        } else {
            // Default behavior: use rack-aware if rack is specified, otherwise dc-aware
            LoadBalanceType defaultStrategy = settings.node().rack != null ? LoadBalanceType.RACK_AWARE : LoadBalanceType.DC_AWARE;
            ret = defaultStrategy.createPolicy(settings);
        }

        if (settings.node().isWhiteList)
            ret = new WhiteListPolicy(ret, settings.node().resolveAll(settings.port().nativePort));
        return new TokenAwarePolicy(ret, ReplicaOrdering.RANDOM);
    }

    public PreparedStatement prepare(String query) {
        PreparedStatement stmt = stmts.get(query);
        if (stmt != null)
            return stmt;
        synchronized (stmts) {
            stmt = stmts.get(query);
            if (stmt != null)
                return stmt;
            stmt = new PreparedStatement(getSession().prepare(query));
            stmts.put(query, stmt);
        }
        return stmt;
    }

    public void connect(ProtocolCompression compression) throws Exception {
        PoolingOptions poolingOpts = new PoolingOptions()
                .setConnectionsPerHost(HostDistance.LOCAL, connectionsPerHost, connectionsPerHost)
                .setNewConnectionThreshold(HostDistance.LOCAL, 100);

        if (maxPendingPerConnection != null) {
            poolingOpts.setMaxRequestsPerConnection(HostDistance.LOCAL, maxPendingPerConnection);
        }

        Cluster.Builder clusterBuilder = Cluster.builder();

        clusterBuilder.addContactPoints(hosts.toArray(new String[0]));

        clusterBuilder.withPort(port)
                .withPoolingOptions(poolingOpts)
                .withoutJMXReporting()
                .withProtocolVersion(protocolVersion)
                .withoutMetrics(); // The driver uses metrics 3 with conflict with our version

        if (loadBalancingPolicy != null) {
            clusterBuilder.withLoadBalancingPolicy(loadBalancingPolicy);
        }

        clusterBuilder.withCompression(compression.ToJavaDriverV3());

        if (encryptionOptions.enabled) {
            SSLContext sslContext;
            sslContext = SSLFactory.createSSLContext(encryptionOptions, true);

            RemoteEndpointAwareJdkSSLOptions sslOptions = new RemoteEndpointAwareJdkSSLOptions(sslContext, encryptionOptions.cipher_suites) {
                @Override
                protected SSLEngine newSSLEngine(SocketChannel channel, InetSocketAddress remoteEndpoint) {
                    SSLEngine engine = super.newSSLEngine(channel, remoteEndpoint);
                    if (encryptionOptions.hostname_verification) {
                        SSLParameters parameters = engine.getSSLParameters();
                        parameters.setEndpointIdentificationAlgorithm("HTTPS");
                        engine.setSSLParameters(parameters);
                    }
                    return engine;
                }
            };

            clusterBuilder.withSSL(sslOptions);
        }

        if (authProvider != null) {
            clusterBuilder.withAuthProvider(authProvider);
        } else if (username != null) {
            clusterBuilder.withCredentials(username, password);
        }

        try {
            cluster = clusterBuilder.build();
            Metadata metadata = cluster.getMetadata();
            System.out.printf(
                    "Connected to cluster: %s, max pending requests per connection %d, max connections per host %d%n",
                    metadata.getClusterName(),
                    maxPendingPerConnection,
                    connectionsPerHost);
            for (Host host : metadata.getAllHosts()) {
                System.out.printf("Datatacenter: %s; Host: %s; Rack: %s%n",
                        host.getDatacenter(), host.getAddress(), host.getRack());
            }

            session = cluster.connect();
        } catch (NoHostAvailableException e) {
            Throwable sslException = findExceptionInErrors(e, SSLHandshakeException.class);
            if (sslException != null)
                System.err.println(String.format(
                        "  Failed to connect to node due to an error during SSL handshake %s: %s",
                        sslException.getClass().getName(), sslException.getMessage()));
            throw e;
        }
    }

    private Throwable findExceptionInErrors(NoHostAvailableException e, Class<? extends Throwable> exceptionClass) {
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

    public Cluster getCluster() {
        return cluster;
    }

    public Session getSession() {
        return session;
    }

    public void execute(String query, com.scylladb.utils.ConsistencyLevel consistency) {
        SimpleStatement stmt = new SimpleStatement(query);
        stmt.setConsistencyLevel(consistency.ToV3Value());
        session.execute(stmt);
    }

    public ResultSet execute(String query, com.scylladb.utils.ConsistencyLevel consistency,
                             com.scylladb.utils.ConsistencyLevel serialConsistency) {
        SimpleStatement stmt = new SimpleStatement(query);
        if (consistency != null)
            stmt.setConsistencyLevel(consistency.ToV3Value());
        if (serialConsistency != null)
            stmt.setSerialConsistencyLevel(serialConsistency.ToV3Value());
        return getSession().execute(stmt);
    }

    public ResultSet executePrepared(PreparedStatement stmt, List<Object> queryParams, com.scylladb.utils.ConsistencyLevel consistency, com.scylladb.utils.ConsistencyLevel serialConsistency) {
        if (stmt.getConsistencyLevel() == null) {
            stmt.setConsistencyLevel(consistency);
        }

        if (stmt.getSerialConsistencyLevel() == null) {
            stmt.setSerialConsistencyLevel(serialConsistency);
        }

        BoundStatement bstmt = stmt.bind(queryParams.toArray(new Object[queryParams.size()]));
        return getSession().execute(bstmt.ToV3Value());
    }

    public void disconnect() {
        try {
            cluster.close();
        } catch (Exception e) {
            System.out.printf(
                    "Failed to close connection due to the following error: %s",
                    e);
        }
    }

    public TableMetadata getTableMetadata(String keyspace, String tableName) {
        return new TableMetadata(getSession().getCluster().getMetadata().getKeyspace(keyspace).getTable(tableName));
    }
}
