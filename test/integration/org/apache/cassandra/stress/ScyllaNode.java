package org.apache.cassandra.stress;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.testcontainers.containers.Network;
import org.testcontainers.scylladb.ScyllaDBContainer;
import org.testcontainers.utility.DockerImageName;

final class ScyllaNode {
    static final String DATACENTER = "datacenter1";
    static final String NETWORK_ALIAS = "scylla";
    static final Network NETWORK = Network.newNetwork();

    private static final String IMAGE = System.getProperty("scylla.image", "scylladb/scylla:2025.1");

    private static ScyllaDBContainer container;

    private ScyllaNode() {}

    static synchronized ScyllaDBContainer container() {
        if (container == null) {
            ScyllaDBContainer started = new ScyllaDBContainer(
                            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("scylladb/scylla"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases(NETWORK_ALIAS);
            started.start();
            container = started;
        }
        return container;
    }

    static String host() {
        return container().getContactPoint().getHostString();
    }

    static int port() {
        return container().getContactPoint().getPort();
    }

    static CqlSession session() {
        InetSocketAddress contactPoint = container().getContactPoint();
        return CqlSession.builder()
                .addContactPoint(contactPoint)
                .withLocalDatacenter(DATACENTER)
                .withConfigLoader(DriverConfigLoader.programmaticBuilder()
                        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(60))
                        .build())
                .build();
    }

    static long count(String keyspace, String table) {
        try (CqlSession session = session()) {
            return session.execute("SELECT count(*) FROM " + keyspace + "." + table)
                    .one()
                    .getLong(0);
        }
    }

    static void dropKeyspace(String keyspace) {
        try (CqlSession session = session()) {
            session.execute("DROP KEYSPACE IF EXISTS " + keyspace);
        }
    }
}
