package org.apache.cassandra.stress.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsGraphTest {
    @TempDir
    Path dir;

    private SettingsGraph graph(String... graphOptions) {
        String[] args = new String[graphOptions.length + 4];
        args[0] = "write";
        args[1] = "n=10";
        args[2] = "-graph";
        args[3] = "file=" + dir.resolve("graph.html");
        System.arraycopy(graphOptions, 0, args, 4, graphOptions.length);
        return StressSettings.parse(args).graph;
    }

    @Test
    void defaultTitleIsTheCurrentDateAndTime() {
        String title = graph().title;
        assertTrue(title.matches("cassandra-stress - \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), title);
    }

    @Test
    void readsTheTitleRevisionAndOperation() {
        SettingsGraph graph = graph("title=nightly", "revision=r2", "op=load");
        assertEquals("nightly", graph.title);
        assertEquals("r2", graph.revision);
        assertEquals("load", graph.operation);
    }

    @Test
    void operationDefaultsToTheCommand() {
        SettingsGraph graph = graph();
        assertEquals("WRITE", graph.operation);
        assertEquals("unknown", graph.revision);
        assertTrue(graph.inGraphMode());
    }

    @Test
    void graphModeIsOffWithoutGraphOptions() {
        assertFalse(StressSettings.parse(new String[] {"write", "n=10"}).graph.inGraphMode());
    }
}
