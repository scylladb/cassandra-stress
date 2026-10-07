// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.cassandra.stress.util.ResultLogger;

public class SettingsGraph {
    private static final DateTimeFormatter TITLE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    public final String file;
    public final String revision;
    public final String title;
    public final String operation;
    public final File temporaryLogFile;

    public SettingsGraph(GraphOptions options, SettingsCommand stressCommand) {
        file = options.file.value();
        revision = options.revision.value();
        title = options.title.value() == null
                ? "cassandra-stress - "
                        + LocalDateTime.now(ZoneId.systemDefault()).format(TITLE_TIME)
                : options.title.value();

        operation = options.operation.value() == null ? stressCommand.type.name() : options.operation.value();

        if (inGraphMode()) {
            temporaryLogFile = createTemporaryLogFile();
        } else {
            temporaryLogFile = null;
        }
    }

    private static File createTemporaryLogFile() {
        try {
            return File.createTempFile("cassandra-stress", ".log");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void deleteTemporaryLogFile() {
        if (temporaryLogFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryLogFile.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean inGraphMode() {
        return this.file != null;
    }

    private static final class GraphOptions extends GroupedOptions {
        final OptionSimple file = new OptionSimple("file=", ".*", null, "HTML file to create or append to", true);
        final OptionSimple revision = new OptionSimple(
                "revision=",
                ".*",
                "unknown",
                "Unique name to assign to the current configuration being stressed",
                false);
        final OptionSimple title =
                new OptionSimple("title=", ".*", null, "Title for chart (current date by default)", false);
        final OptionSimple operation = new OptionSimple(
                "op=", ".*", null, "Alternative name for current operation (stress op name used by default)", false);

        @Override
        public List<? extends Option> options() {
            return Arrays.asList(file, revision, title, operation);
        }
    }

    public void printSettings(ResultLogger out) {
        out.println("  File: " + file);
        out.println("  Revision: " + revision);
        out.println("  Title: " + title);
        out.println("  Operation: " + operation);
    }

    public static SettingsGraph get(Map<String, String[]> clArgs, SettingsCommand stressCommand) {
        String[] params = clArgs.remove("-graph");
        if (params == null) {
            return new SettingsGraph(new GraphOptions(), stressCommand);
        }
        GraphOptions options = GroupedOptions.select(params, new GraphOptions());
        if (options == null) {
            throw new InvalidSettingsException(
                    "Invalid -graph options provided, see output for valid options", SettingsGraph::printHelp);
        }
        return new SettingsGraph(options, stressCommand);
    }

    public static void printHelp() {
        GroupedOptions.printOptions(System.out, "-graph", new GraphOptions());
    }

    public static Runnable helpPrinter() {
        return () -> printHelp();
    }
}
