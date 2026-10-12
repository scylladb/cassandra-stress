package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsNodeTest {
    private static SettingsNode parse(String... params) {
        return SettingsNode.get(new HashMap<>(Map.of("-node", params)));
    }

    @Test
    void readsRemoteDc() {
        assertEquals(5, parse("remote-dc=5").usedHostsPerRemoteDc);
    }

    @Test
    void leavesRemoteDcUnsetByDefault() {
        assertNull(parse().usedHostsPerRemoteDc);
    }

    @Test
    void readsRemoteDcWithOtherOptions() {
        SettingsNode settings = parse("datacenter=dc1", "remote-dc=3", "localhost");
        assertEquals("dc1", settings.datacenter);
        assertEquals(3, settings.usedHostsPerRemoteDc);
    }

    @Test
    void rejectsZeroRemoteDc() {
        assertThrows(IllegalArgumentException.class, () -> parse("remote-dc=0"));
    }

    @Test
    void readsNodesFromAFileAndSkipsEmptyLines(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("nodes"), "10.0.0.1\n\n10.0.0.2\n");
        assertEquals(List.of("10.0.0.1", "10.0.0.2"), parse("file=" + file).nodes);
    }

    @Test
    void readsCommaSeparatedNodes() {
        assertEquals(List.of("10.0.0.1", "10.0.0.2"), parse("10.0.0.1,10.0.0.2").nodes);
    }
}
