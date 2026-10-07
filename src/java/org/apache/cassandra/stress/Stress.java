// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.util.Locale;
import org.apache.cassandra.stress.settings.InvalidSettingsException;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.MultiResultLogger;
import sun.misc.Signal;
import sun.misc.SignalHandler;

public final class Stress {
    private Stress() {}

    private static volatile boolean stopped;

    public static void main(String[] arguments) throws Exception {
        registerSignalHandler();

        int exitCode = run(arguments);

        System.exit(exitCode);
    }

    static int run(String[] arguments) {
        try {
            final StressSettings settings;
            try {
                settings = StressSettings.parse(arguments);
                if (settings == null) {
                    return 0;
                }
            } catch (InvalidSettingsException e) {
                e.printHelp();
                System.out.println(e.getMessage());
                return 1;
            } catch (IllegalArgumentException e) {
                System.out.printf(Locale.ROOT, "%s%n", e.getMessage());
                printHelpMessage();
                return 1;
            }

            return run(settings, arguments);
        } catch (Throwable t) {
            t.printStackTrace();
            return 1;
        }
    }

    static int run(StressSettings settings, String[] arguments) throws Exception {
        try (MultiResultLogger logout = settings.log.getOutput()) {
            settings.setOutput(logout);
            try {
                return run(settings, arguments, logout);
            } catch (Exception e) {
                logout.printFailureToOwnedStreams(e);
                throw e;
            } finally {
                settings.setOutput(new MultiResultLogger(System.out));
            }
        } finally {
            settings.graph.deleteTemporaryLogFile();
        }
    }

    private static int run(StressSettings settings, String[] arguments, MultiResultLogger logout) throws Exception {
        if (!settings.log.noSettings) {
            settings.printSettings(logout);
        }

        if (settings.graph.inGraphMode() && settings.sendToDaemon == null) {
            logout.addOwnedStream(
                    new PrintStream(Files.newOutputStream(settings.graph.temporaryLogFile), false, UTF_8));
        }

        if (settings.sendToDaemon != null) {
            if (!sendToDaemon(HostAndPort.parse(settings.sendToDaemon, StressServer.DEFAULT_PORT), arguments, logout)) {
                return 1;
            }
        } else {
            StressAction stressAction = new StressAction(settings, logout);
            stressAction.run();
            logout.flush();
            if (settings.graph.inGraphMode()) {
                new StressGraph(settings, arguments).generateGraph();
            }
        }

        return 0;
    }

    static boolean sendToDaemon(HostAndPort daemon, String[] arguments, MultiResultLogger logout) throws IOException {
        try (Socket socket = new Socket(daemon.host(), daemon.port());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                BufferedReader inp = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8))) {
            Runtime.getRuntime().addShutdownHook(new ShutDown(socket, out));
            StressServer.writeCommand(out, arguments);
            try {
                String line;
                while (!socket.isClosed() && (line = inp.readLine()) != null) {
                    if ("END".equals(line) || "FAILURE".equals(line)) {
                        acknowledge(out);
                        return "END".equals(line);
                    }
                    logout.println(line);
                }
            } catch (SocketException e) {
                if (!stopped) {
                    throw e;
                }
            }
            return false;
        }
    }

    @SuppressWarnings("EmptyCatch")
    private static void acknowledge(DataOutputStream out) {
        try {
            out.writeInt(1);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    public static void printHelpMessage() {
        StressSettings.printHelp();
    }

    private static class ShutDown extends Thread {
        private final Socket socket;
        private final DataOutputStream out;

        ShutDown(Socket socket, DataOutputStream out) {
            this.out = out;
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                if (!socket.isClosed()) {
                    System.out.println("Control-C caught. Canceling running action and shutting down...");

                    out.writeInt(1);
                    out.close();

                    stopped = true;
                }
            } catch (IOException e) {
                System.err.println("Failed to stop the remote run: " + e);
            }
        }
    }

    private static String threadDump(boolean lockedMonitors, boolean lockedSynchronizers) {
        StringBuilder threadDump = new StringBuilder(System.lineSeparator());
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers)) {
            threadDump.append(threadInfo.toString());
        }
        return threadDump.toString();
    }

    private static void registerSignalHandler() {
        SignalHandler handler = signal -> {
            System.out.println("Caught signal: " + signal.getName());
            System.out.println(threadDump(true, true));
            System.exit(
                    switch (signal.getName()) {
                        case "ABRT" -> 128 + 6;
                        case "TERM" -> 128 + 15;
                        case "INT" -> 128 + 2;
                        default -> 1;
                    });
        };
        Signal.handle(new Signal("ABRT"), handler);
        Signal.handle(new Signal("TERM"), handler);
        Signal.handle(new Signal("INT"), handler);
    }
}
