package org.apache.cassandra.stress.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HdrLogTest {
    @TempDir
    Path dir;

    private static Histogram histogram(String tag, long start) {
        Histogram histogram = new Histogram(3);
        histogram.recordValue(42);
        histogram.setTag(tag);
        histogram.setStartTimeStamp(start);
        histogram.setEndTimeStamp(start + 1000);
        return histogram;
    }

    @Test
    void opensNothingWithoutAFile() {
        assertNull(HdrLog.open(null));
    }

    @Test
    void keepsTheHistogramsOfEveryStepUnderOneHeader() throws IOException {
        Path file = dir.resolve("stress.hdr");
        long now = System.currentTimeMillis();

        try (HdrLog log = HdrLog.open(file)) {
            log.write(histogram("WRITE-st", now));
            log.write(histogram("WRITE-st", now + 1000));
            log.write(histogram("WRITE-st", now + 2000));
        }

        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.stream().filter(l -> l.startsWith("#[BaseTime:")).count());
        assertEquals(1, lines.stream().filter(l -> l.startsWith("#[StartTime:")).count());
        assertEquals(
                3, lines.stream().filter(l -> l.startsWith("Tag=WRITE-st,")).count());
    }

    @Test
    void closeReleasesTheFile() throws IOException {
        Path file = dir.resolve("closed.hdr");
        HdrLog log = HdrLog.open(file);
        log.write(histogram("READ-rt", System.currentTimeMillis()));

        log.close();

        assertTrue(Files.deleteIfExists(file));
    }
}
