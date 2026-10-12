package org.apache.cassandra.stress.driver.v3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datastax.driver.core.Cluster;
import org.junit.jupiter.api.Test;

class V3ClientProviderTest {
    @Test
    void reportsTheVersionThatTheDriverReports() {
        assertEquals(Cluster.getDriverVersion(), new V3ClientProvider().version());
    }
}
