// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import com.datastax.driver.core.AuthProvider;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.RemoteEndpointAwareJdkSSLOptions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy.ReplicaOrdering;
import com.datastax.driver.core.policies.WhiteListPolicy;
import com.datastax.shaded.netty.channel.socket.SocketChannel;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import org.apache.cassandra.stress.core.BoundStatement;
import org.apache.cassandra.stress.core.PreparedStatement;
import org.apache.cassandra.stress.core.TableMetadata;
import org.apache.cassandra.stress.settings.LoadBalanceType;
import org.apache.cassandra.stress.settings.ProtocolCompression;
import org.apache.cassandra.stress.settings.StressSettings;

public class JavaDriverClient implements QueryExecutor, QueryPrepare, MetadataProvider {

    public final List<String> hosts;
    public final int port;
    public final String username;
    public final String password;
    public final AuthProvider authProvider;
    public final Integer maxPendingPerConnection;
    public final int connectionsPerHost;
    public final int requestTimeout;

    private final ProtocolVersion protocolVersion;
    private final EncryptionOptions encryptionOptions;
    private Cluster cluster;
    private Session session;
    private final LoadBalancingPolicy loadBalancingPolicy;

    private final ConcurrentMap<String, PreparedStatement> stmts = new ConcurrentHashMap<>();

    public JavaDriverClient(StressSettings settings, List<String> hosts, int port) {
        this(settings, hosts, port, new EncryptionOptions());
    }

    public JavaDriverClient(
            StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions) {
        this.protocolVersion = settings.mode.protocolVersion.toJavaDriverV3();
        this.hosts = hosts;
        this.port = port;
        this.username = settings.mode.username;
        this.password = settings.mode.password;
        this.authProvider = settings.mode.authProvider.toJavaDriverV3();
        this.encryptionOptions = encryptionOptions;
        this.loadBalancingPolicy = loadBalancingPolicy(settings);
        this.connectionsPerHost = Objects.requireNonNullElse(settings.mode.connectionsPerHost, 8);
        this.requestTimeout = Objects.requireNonNullElse(settings.mode.requestTimeout, 12000);

        maxPendingPerConnection = settings.mode.maxPendingPerConnection;
    }

    private LoadBalancingPolicy loadBalancingPolicy(StressSettings settings) {
        LoadBalancingPolicy ret;

        if (settings.node.loadBalance != null) {
            ret = settings.node.loadBalance.createPolicy(settings);
        } else {
            LoadBalanceType defaultStrategy =
                    settings.node.rack != null ? LoadBalanceType.RACK_AWARE : LoadBalanceType.DC_AWARE;
            ret = defaultStrategy.createPolicy(settings);
        }

        if (settings.node.isWhiteList)
            ret = new WhiteListPolicy(ret, settings.node.resolveAll(settings.port.nativePort));
        return new TokenAwarePolicy(ret, ReplicaOrdering.RANDOM);
    }

    @Override
    public PreparedStatement prepare(String query) {
        return stmts.computeIfAbsent(
                query, q -> new PreparedStatement(getSession().prepare(q)));
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

        clusterBuilder
                .withPort(port)
                .withPoolingOptions(poolingOpts)
                .withoutJMXReporting()
                .withProtocolVersion(protocolVersion)
                .withoutMetrics()
                .withSocketOptions(new SocketOptions().setReadTimeoutMillis(requestTimeout));

        if (loadBalancingPolicy != null) {
            clusterBuilder.withLoadBalancingPolicy(loadBalancingPolicy);
        }

        clusterBuilder.withCompression(compression.toJavaDriverV3());

        if (encryptionOptions.enabled) {
            SSLContext sslContext;
            sslContext = SSLFactory.createSSLContext(encryptionOptions, true);

            RemoteEndpointAwareJdkSSLOptions sslOptions =
                    new RemoteEndpointAwareJdkSSLOptions(sslContext, encryptionOptions.cipherSuites) {
                        @Override
                        protected SSLEngine newSSLEngine(SocketChannel channel, InetSocketAddress remoteEndpoint) {
                            SSLEngine engine = super.newSSLEngine(channel, remoteEndpoint);
                            if (encryptionOptions.hostnameVerification) {
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
                    metadata.getClusterName(), maxPendingPerConnection, connectionsPerHost);
            for (Host host : metadata.getAllHosts()) {
                System.out.printf(
                        "Datatacenter: %s; Host: %s; Rack: %s%n",
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

    @Override
    public void execute(String query, org.apache.cassandra.stress.util.ConsistencyLevel consistency) {
        SimpleStatement stmt = new SimpleStatement(query);
        stmt.setConsistencyLevel(consistency.toV3Value());
        session.execute(stmt);
    }

    public ResultSet execute(
            String query,
            org.apache.cassandra.stress.util.ConsistencyLevel consistency,
            org.apache.cassandra.stress.util.ConsistencyLevel serialConsistency) {
        SimpleStatement stmt = new SimpleStatement(query);
        if (consistency != null) stmt.setConsistencyLevel(consistency.toV3Value());
        if (serialConsistency != null) stmt.setSerialConsistencyLevel(serialConsistency.toV3Value());
        return getSession().execute(stmt);
    }

    public ResultSet executePrepared(
            PreparedStatement stmt,
            List<Object> queryParams,
            org.apache.cassandra.stress.util.ConsistencyLevel consistency) {
        if (stmt.getConsistencyLevel() == null) stmt.setConsistencyLevel(consistency);
        BoundStatement bstmt = stmt.bind((Object[]) queryParams.toArray(new Object[0]));
        return getSession().execute(bstmt.toV3Value());
    }

    public ResultSet executePrepared(
            PreparedStatement stmt,
            List<Object> queryParams,
            org.apache.cassandra.stress.util.ConsistencyLevel consistency,
            org.apache.cassandra.stress.util.ConsistencyLevel serialConsistency) {
        if (stmt.getConsistencyLevel() == null) stmt.setConsistencyLevel(consistency);
        if (stmt.getSerialConsistencyLevel() == null) stmt.setSerialConsistencyLevel(serialConsistency);
        BoundStatement bstmt = stmt.bind((Object[]) queryParams.toArray(new Object[0]));
        return getSession().execute(bstmt.toV3Value());
    }

    public void disconnect() {
        try {
            cluster.close();
        } catch (Exception e) {
            System.out.printf("Failed to close connection due to the following error: %s", e.toString());
        }
    }

    @Override
    public TableMetadata getTableMetadata(String keyspace, String tableName) {
        com.datastax.driver.core.KeyspaceMetadata keyspaceMetadata =
                getSession().getCluster().getMetadata().getKeyspace(keyspace);
        if (keyspaceMetadata == null) return null;
        com.datastax.driver.core.TableMetadata table = keyspaceMetadata.getTable(tableName);
        return table == null ? null : new TableMetadata(table);
    }
}
