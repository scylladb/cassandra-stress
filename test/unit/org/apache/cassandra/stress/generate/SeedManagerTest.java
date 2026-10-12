package org.apache.cassandra.stress.generate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.cassandra.stress.Operation;
import org.apache.cassandra.stress.WorkManager;
import org.apache.cassandra.stress.driver.StressClient;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.junit.jupiter.api.Test;

class SeedManagerTest {
    private static final class Writer extends Operation {
        Writer(StressSettings settings) {
            super(new Timer("write", (opType, intended, started, ended, rows, partitions, error) -> {}), settings);
        }

        @Override
        public boolean isWrite() {
            return true;
        }

        @Override
        public int ready(WorkManager permits) {
            return 0;
        }

        @Override
        public void run(StressClient client) {}

        @Override
        public String key() {
            return "";
        }
    }

    @Test
    void reportsAPartitionAsWrittenUntilItsLastVisit() {
        StressSettings settings =
                StressSettings.parse(new String[] {"write", "n=10", "-pop", "seq=1..10", "-insert", "visits=fixed(4)"});
        SeedManager seeds = new SeedManager(settings);
        Seed seed = seeds.next(new Writer(settings));

        assertTrue(seeds.isWriting(new Seed(seed.seed, 1)));
        seeds.markLastWrite(seed, false);
        assertFalse(seeds.isWriting(new Seed(seed.seed, 1)));
    }
}
