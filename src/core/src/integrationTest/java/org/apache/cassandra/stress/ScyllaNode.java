package org.apache.cassandra.stress;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.Network;
import org.testcontainers.scylladb.ScyllaDBContainer;
import org.testcontainers.utility.DockerImageName;

final class ScyllaNode {
    static final String DATACENTER = "datacenter1";
    static final String NETWORK_ALIAS = "scylla";
    static final Network NETWORK = Network.newNetwork();

    private static final String IMAGE = System.getProperty(
            "scylla.image",
            "scylladb/scylla:2025.1@sha256:9eb53463bfed32356e1ba81eb6d9c606ffaa520ed804cc03b17713f4b4e7d84b");

    private static final int START_ATTEMPTS = 3;

    private static ScyllaDBContainer container;

    private ScyllaNode() {}

    static synchronized ScyllaDBContainer container() {
        if (container == null) {
            container = start();
        }
        return container;
    }

    // Another process can take the free port before Docker binds it, so a failed
    // start tries again with a new port.
    private static ScyllaDBContainer start() {
        ContainerLaunchException failure = null;
        for (int attempt = 0; attempt < START_ATTEMPTS; attempt++) {
            ScyllaDBContainer node = node(freePort());
            try {
                node.start();
                return node;
            } catch (ContainerLaunchException e) {
                node.stop();
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        throw failure;
    }

    private static ScyllaDBContainer node(int shardAwarePort) {
        ScyllaDBContainer node =
                new ScyllaDBContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("scylladb/scylla")) {
                    {
                        // The driver connects to the shard-aware port that the node advertises,
                        // so the node listens on the same port number on the host and in the container.
                        addFixedExposedPort(shardAwarePort, shardAwarePort);
                    }
                };
        return node.withCommand(
                        "--developer-mode=1 --overprovisioned=1 --native-shard-aware-transport-port=" + shardAwarePort)
                .withNetwork(NETWORK)
                .withNetworkAliases(NETWORK_ALIAS);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
