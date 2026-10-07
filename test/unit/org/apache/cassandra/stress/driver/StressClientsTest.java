package org.apache.cassandra.stress.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.cassandra.stress.driver.v3.JavaDriverV3Client;
import org.apache.cassandra.stress.driver.v4.JavaDriverV4Client;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.EncryptionOptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StressClientsTest {
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"|v3", "-mode cql3|v3", "-mode cql3 native|v3", "-mode native cql3|v3", "-mode cql3 4x|v4"})
    void createsTheClientOfTheSelectedDriver(String mode, String driver) {
        String args = "write n=1" + (mode == null ? "" : " " + mode);
        StressSettings settings = StressSettings.parse(args.split(" "));
        StressClient client = StressClients.create(settings, List.of("127.0.0.1"), 9042, new EncryptionOptions());
        Class<? extends StressClient> expected =
                "v3".equals(driver) ? JavaDriverV3Client.class : JavaDriverV4Client.class;
        assertEquals(expected, client.getClass());
    }
}
