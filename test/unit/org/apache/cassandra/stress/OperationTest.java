package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.oss.driver.api.core.servererrors.OverloadedException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.cassandra.stress.report.Timer;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.JavaDriverClient;
import org.junit.jupiter.api.Test;

class OperationTest {
    @Test
    void previewsShortBuffersInFull() {
        assertEquals("0x00ff10", Operation.hexPreview(ByteBuffer.wrap(new byte[] {0, (byte) 0xff, 0x10}), 16));
    }

    @Test
    void truncatesLongBuffers() {
        assertEquals("0x0102...", Operation.hexPreview(ByteBuffer.wrap(new byte[] {1, 2, 3}), 2));
    }

    @Test
    void previewsFromThePositionAndLeavesItUnchanged() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {1, 2, 3});
        buffer.position(1);
        assertEquals("0x0203", Operation.hexPreview(buffer, 16));
        assertEquals(1, buffer.position());
    }

    @Test
    void previewsNullAndEmpty() {
        assertEquals("null", Operation.hexPreview(null, 16));
        assertEquals("0x", Operation.hexPreview(ByteBuffer.allocate(0), 16));
    }

    private static final class Probe extends Operation {
        Probe(StressSettings settings) {
            super(new Timer("probe", (opType, intended, started, ended, rows, partitions, error) -> {}), settings);
        }

        @Override
        public int ready(WorkManager permits) {
            return 1;
        }

        @Override
        public void run(JavaDriverClient client) {}

        @Override
        public String key() {
            return "[k]";
        }
    }

    private static Operation.RunOp overloadedEveryTime(AtomicInteger tries) {
        return new Operation.RunOp() {
            @Override
            public boolean run() {
                tries.incrementAndGet();
                throw new OverloadedException(null, "Too many in flight hints");
            }

            @Override
            public int partitionCount() {
                return 0;
            }

            @Override
            public int rowCount() {
                return 0;
            }
        };
    }

    @Test
    void namesTheOverloadWhenTheLastTryIsOverloaded() {
        StressSettings settings = StressSettings.parse(new String[] {"write", "n=10", "-errors", "retries=2"});
        AtomicInteger tries = new AtomicInteger();
        IOException e =
                assertThrows(IOException.class, () -> new Probe(settings).timeWithRetry(overloadedEveryTime(tries)));
        assertEquals(3, tries.get());
        assertTrue(e.getMessage().contains("Error executing: (OverloadedException)"), e.getMessage());
        assertTrue(e.getMessage().contains("Too many in flight hints"), e.getMessage());
    }

    @Test
    void doesNotWaitAfterTheLastTry() {
        StressSettings settings = StressSettings.parse(new String[] {
            "write", "n=10", "-errors", "retries=0", "delay-policy=constant", "min-delay-ms=10000", "max-delay-ms=10000"
        });
        long started = System.nanoTime();
        assertThrows(
                IOException.class, () -> new Probe(settings).timeWithRetry(overloadedEveryTime(new AtomicInteger())));
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5));
    }
}
