package org.apache.cassandra.stress;

import com.datastax.oss.driver.api.core.CqlSession;
import java.net.InetSocketAddress;
import org.testcontainers.scylladb.ScyllaDBContainer;
import org.testcontainers.utility.DockerImageName;

final class ScyllaNode {
    static final String DATACENTER = "datacenter1";

    private static final String IMAGE = System.getProperty("scylla.image", "scylladb/scylla:2025.1");

    private static ScyllaDBContainer container;

    private ScyllaNode() {}

    static synchronized ScyllaDBContainer container() {
        if (container == null) {
            ScyllaDBContainer started =
                    new ScyllaDBContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("scylladb/scylla"));
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
