package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
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
}
