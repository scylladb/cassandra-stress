package org.apache.cassandra.stress.driver.v4;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datastax.oss.driver.api.core.session.Session;
import org.junit.jupiter.api.Test;

class V4ClientProviderTest {
    @Test
    void reportsTheVersionThatTheDriverReports() {
        assertEquals(Session.OSS_DRIVER_COORDINATES.getVersion().toString(), new V4ClientProvider().version());
    }
}
