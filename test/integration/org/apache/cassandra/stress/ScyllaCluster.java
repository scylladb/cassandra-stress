package org.apache.cassandra.stress;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.Network;
import org.testcontainers.scylladb.ScyllaDBContainer;
import org.testcontainers.utility.DockerImageName;

final class ScyllaCluster implements AutoCloseable {
    private final Network network = Network.newNetwork();
    private final List<ScyllaDBContainer> nodes = new ArrayList<>();

    ScyllaCluster(int size) {
        for (int i = 1; i <= size; i++) {
            ScyllaDBContainer node = new ScyllaDBContainer(
                            DockerImageName.parse(ScyllaNode.IMAGE).asCompatibleSubstituteFor("scylladb/scylla"))
                    .withNetwork(network)
                    .withNetworkAliases("node" + i)
                    .withCommand("--developer-mode=1 --overprovisioned=1 --smp=1 --memory=768M --seeds=node1")
                    .withStartupTimeout(Duration.ofMinutes(2));
            node.start();
            nodes.add(node);
        }
    }

    String host() {
        return ip(0);
    }

    private String ip(int index) {
        return nodes.get(index)
                .getContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .values()
                .iterator()
                .next()
                .getIpAddress();
    }

    int port() {
        return 9042;
    }

    void pause(int index) {
        DockerClientFactory.instance()
                .client()
                .pauseContainerCmd(nodes.get(index).getContainerId())
                .exec();
    }

    void unpause(int index) {
        DockerClientFactory.instance()
                .client()
                .unpauseContainerCmd(nodes.get(index).getContainerId())
                .exec();
    }

    boolean isPaused(int index) {
        return Boolean.TRUE.equals(DockerClientFactory.instance()
                .client()
                .inspectContainerCmd(nodes.get(index).getContainerId())
                .exec()
                .getState()
                .getPaused());
    }

    void awaitAllUp(Duration timeout) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            String status =
                    nodes.getFirst().execInContainer("nodetool", "status").getStdout();
            if (status.lines().filter(line -> line.startsWith("UN")).count() == nodes.size()) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("Not every node is up and normal after " + timeout);
    }

    long requestsServed(int index) throws IOException, InterruptedException {
        return nodes.get(index)
                .execInContainer("curl", "-s", "http://" + ip(index) + ":9180/metrics")
                .getStdout()
                .lines()
                .filter(line -> line.startsWith("scylla_transport_requests_served{"))
                .mapToLong(ScyllaCluster::metricValue)
                .sum();
    }

    private static long metricValue(String line) {
        try {
            return (long) Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Cannot parse the metric line " + line, e);
        }
    }

    void dropKeyspace(String keyspace) throws IOException, InterruptedException {
        Container.ExecResult drop =
                nodes.getFirst().execInContainer("cqlsh", ip(0), "-e", "DROP KEYSPACE IF EXISTS " + keyspace);
        if (drop.getExitCode() != 0) {
            throw new IllegalStateException("Cannot drop " + keyspace + ": " + drop.getStderr());
        }
    }

    @Override
    public void close() {
        for (ScyllaDBContainer node : nodes.reversed()) {
            node.stop();
        }
        network.close();
    }
}
