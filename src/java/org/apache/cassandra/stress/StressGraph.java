// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.cassandra.stress.report.StressMetrics;
import org.apache.cassandra.stress.settings.StressSettings;

public class StressGraph
{
    private static final ObjectMapper JSON = new ObjectMapper();

    private StressSettings stressSettings;
    private enum ReadingMode
    {
        START,
        METRICS,
        AGGREGATES,
        NEXTITERATION
    }
    private String[] stressArguments;

    public StressGraph(StressSettings stressSetttings, String[] stressArguments)
    {
        this.stressSettings = stressSetttings;
        this.stressArguments = stressArguments;
    }

    public void generateGraph()
    {
        Path htmlFile = Paths.get(stressSettings.graph.file);
        ObjectNode stats;
        if (Files.isRegularFile(htmlFile))
        {
            try
            {
                stats = parseExistingStats(Files.readString(htmlFile));
            }
            catch (IOException e)
            {
                throw new RuntimeException("Couldn't load existing stats html.");
            }
            stats = this.createJSONStats(stats);
        }
        else
        {
            stats = this.createJSONStats(null);
        }

        try
        {
            String statsBlock = "/* stats start */\nstats = " + JSON.writeValueAsString(stats) + ";\n/* stats end */\n";
            String html = getGraphHTML().replaceFirst("/\\* stats start \\*/\n\n/\\* stats end \\*/\n", Matcher.quoteReplacement(statsBlock));
            Files.writeString(htmlFile, html);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Couldn't write stats html.");
        }
    }

    private ObjectNode parseExistingStats(String html) throws IOException
    {
        ObjectNode stats;

        Pattern pattern = Pattern.compile("(?s).*/\\* stats start \\*/\\nstats = (.*);\\n/\\* stats end \\*/.*");
        Matcher matcher = pattern.matcher(html);
        matcher.matches();
        stats = (ObjectNode) JSON.readTree(matcher.group(1));

        return stats;
    }

    private String getGraphHTML()
    {
        InputStream graphHTMLRes = StressGraph.class.getClassLoader().getResourceAsStream("org/apache/cassandra/stress/graph/graph.html");
        String graphHTML;
        try
        {
            graphHTML = new String(graphHTMLRes.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        return graphHTML;
    }

    private ArrayNode parseLogStats(InputStream log, ArrayNode stats) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(log, StandardCharsets.UTF_8));
        ObjectNode json = JSON.createObjectNode();
        ArrayNode intervals = JSON.createArrayNode();
        boolean runningMultipleThreadCounts = false;
        String currentThreadCount = null;
        Pattern threadCountMessage = Pattern.compile("Running ([A-Z]+) with ([0-9]+) threads .*");
        ReadingMode mode = ReadingMode.START;

        try
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith("Thread count was not specified"))
                    runningMultipleThreadCounts = true;

                if (runningMultipleThreadCounts)
                {
                    Matcher tc = threadCountMessage.matcher(line);
                    if (tc.matches())
                    {
                        currentThreadCount = tc.group(2);
                    }
                }
                
                if (line.equals(StressMetrics.HEAD))
                {
                    mode = ReadingMode.METRICS;
                    continue;
                }
                else if (line.equals("Results:"))
                {
                    mode = ReadingMode.AGGREGATES;
                    continue;
                }
                else if (mode == ReadingMode.AGGREGATES && line.equals(""))
                {
                    mode = ReadingMode.NEXTITERATION;
                }
                else if (line.equals("END") || line.equals("FAILURE"))
                {
                    break;
                }

                if (mode == ReadingMode.METRICS)
                {
                    String[] parts = line.split(",");
                    if (parts.length != StressMetrics.HEADMETRICS.length)
                    {
                        continue;
                    }
                    ArrayNode metrics = intervals.addArray();
                    for (String m : parts)
                    {
                        try
                        {
                            metrics.add(new BigDecimal(m.trim()));
                        }
                        catch (NumberFormatException e)
                        {
                            metrics.addNull();
                        }
                    }
                }
                else if (mode == ReadingMode.AGGREGATES)
                {
                    String[] parts = line.split(":",2);
                    if (parts.length != 2)
                    {
                        continue;
                    }
                    json.put(parts[0].trim().toLowerCase(Locale.ROOT), parts[1].trim());
                }
                else if (mode == ReadingMode.NEXTITERATION)
                {
                    ArrayNode metricNames = json.putArray("metrics");
                    for (String name : StressMetrics.HEADMETRICS)
                        metricNames.add(name);
                    json.put("test", stressSettings.graph.operation);
                    if (currentThreadCount == null)
                        json.put("revision", stressSettings.graph.revision);
                    else
                        json.put("revision", String.format("%s - %s threads", stressSettings.graph.revision, currentThreadCount));
                    String command = String.join(" ", stressArguments).replaceAll("password=.*? ", "password=******* ");
                    json.put("command", command);
                    json.set("intervals", intervals);
                    stats.add(json);

                    json = JSON.createObjectNode();
                    intervals = JSON.createArrayNode();
                    mode = ReadingMode.START;
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Couldn't read from temporary stress log file");
        }
        if (json.size() != 0) stats.add(json);
        return stats;
    }

    private ObjectNode createJSONStats(ObjectNode json)
    {
        try (InputStream logStream = Files.newInputStream(stressSettings.graph.temporaryLogFile.toPath()))
        {
            ArrayNode stats;
            if (json == null)
            {
                json = JSON.createObjectNode();
                stats = JSON.createArrayNode();
            }
            else
            {
                stats = (ArrayNode) json.get("stats");
            }

            stats = parseLogStats(logStream, stats);

            json.put("title", stressSettings.graph.title);
            json.set("stats", stats);
            return json;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
