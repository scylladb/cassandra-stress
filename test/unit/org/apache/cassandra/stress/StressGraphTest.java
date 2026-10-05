package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.cassandra.stress.report.StressMetrics;
import org.apache.cassandra.stress.settings.StressSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StressGraphTest {
    private static final Pattern STATS =
            Pattern.compile("(?s).*/\\* stats start \\*/\\nstats = (.*);\\n/\\* stats end \\*/.*");

    private static final String LOG = String.join(
            "\n",
            StressMetrics.HEAD,
            "total,          100,     100,     100,     100,     1.0,     0.9,     2.0,     3.0,     4.0,     5.0,   "
                    + " 1.0,  0.00000,      0",
            "total,          250,     150,     150,     150,     1.5,     1.1,     2.5,     3.5,     4.5,     5.5,   "
                    + " 2.0,  0.01000,      0",
            "",
            "Results:",
            "Op rate                   :      125 op/s  [WRITE: 125 op/s]",
            "Total errors              :        0 [WRITE: 0]",
            "",
            "END",
            "");

    @TempDir
    Path dir;

    private JsonNode generate(String title) throws Exception {
        Path html = dir.resolve("graph.html");
        String[] args = {
            "write",
            "n=250",
            "-graph",
            "file=" + html,
            "title=" + title,
            "revision=r1",
            "-mode",
            "cql3",
            "native",
            "user=u",
            "password=secret",
            "-node",
            "127.0.0.1"
        };
        StressSettings settings = StressSettings.parse(args.clone());
        Files.writeString(settings.graph.temporaryLogFile.toPath(), LOG);
        new StressGraph(settings, args).generateGraph();

        Matcher matcher = STATS.matcher(Files.readString(html));
        assertTrue(matcher.matches());
        return new ObjectMapper().readTree(matcher.group(1));
    }

    @Test
    void writesTheRunAsJson() throws Exception {
        JsonNode stats = generate("nightly");
        assertEquals("nightly", stats.get("title").asText());
        assertEquals(1, stats.get("stats").size());

        JsonNode run = stats.get("stats").get(0);
        assertEquals("WRITE", run.get("test").asText());
        assertEquals("r1", run.get("revision").asText());
        assertEquals("125 op/s  [WRITE: 125 op/s]", run.get("op rate").asText());
        assertEquals(StressMetrics.HEADMETRICS.size(), run.get("metrics").size());
        assertEquals(2, run.get("intervals").size());
        assertTrue(run.get("intervals").get(0).get(0).isNull());
        assertEquals(250, run.get("intervals").get(1).get(1).asInt());
        assertEquals(1.5, run.get("intervals").get(1).get(5).asDouble());
        assertTrue(
                run.get("command").asText().contains("password=******* "),
                run.get("command").asText());
    }

    @Test
    void appendsToAnExistingGraph() throws Exception {
        generate("nightly");
        assertEquals(2, generate("nightly").get("stats").size());
    }

    @Test
    void keepsQuotesBackslashesAndDollarsInTheTitle() throws Exception {
        String title = "run\"a\"\\$1";
        assertEquals(title, generate(title).get("title").asText());
    }
}
