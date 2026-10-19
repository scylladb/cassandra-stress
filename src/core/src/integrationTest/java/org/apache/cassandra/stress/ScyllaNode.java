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

    static final String IMAGE = System.getProperty(
            "scylla.image",
            "scylladb/scylla:2026.3@sha256:f96119dbbfa8bad4abe6161f11ac4f6d5a5c34fdc297fd23cf32626791ed2e59");

    private static final int START_ATTEMPTS = 3;

    private static final Object LOCK = new Object();

    private static ScyllaDBContainer container;

    private ScyllaNode() {}

    static ScyllaDBContainer container() {
        synchronized (LOCK) {
            if (container == null) {
                container = start();
            }
            return container;
        }
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

    // The caller starts and keeps the node. The subclass reaches the protected addFixedExposedPort.
    @SuppressWarnings({"PMD.CloseResource", "PMD.DoubleBraceInitialization"})
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
                        "--developer-mode=1 --overprovisioned=1 --smp=1 --memory=1G --native-shard-aware-transport-port="
                                + shardAwarePort)
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
