// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Socket;
import java.net.SocketException;

import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.MultiResultLogger;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public final class Stress
{

    private static volatile boolean stopped = false;

    public static void main(String[] arguments) throws Exception
    {
        registerSignalHandler();

        int exitCode = run(arguments);

        System.exit(exitCode);
    }

    static int run(String[] arguments)
    {
        try
        {
            final StressSettings settings;
            try
            {
                settings = StressSettings.parse(arguments);
                if (settings == null)
                    return 0;
            }
            catch (IllegalArgumentException e)
            {
                System.out.printf("%s%n", e.getMessage());
                printHelpMessage();
                return 1;
            }

            MultiResultLogger logout = settings.log.getOutput();

            if (! settings.log.noSettings)
            {
                settings.printSettings(logout);
            }

            if (settings.graph.inGraphMode())
            {
                logout.addStream(new PrintStream(settings.graph.temporaryLogFile));
            }

            if (settings.sendToDaemon != null)
            {
                Socket socket = new Socket(settings.sendToDaemon, 2159);

                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                BufferedReader inp = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                Runtime.getRuntime().addShutdownHook(new ShutDown(socket, out));

                out.writeObject(settings);

                String line;

                try
                {
                    while (!socket.isClosed() && (line = inp.readLine()) != null)
                    {
                        if (line.equals("END") || line.equals("FAILURE"))
                        {
                            out.writeInt(1);
                            break;
                        }

                        logout.println(line);
                    }
                }
                catch (SocketException e)
                {
                    if (!stopped)
                        e.printStackTrace();
                }

                out.close();
                inp.close();

                socket.close();
            }
            else
            {
                StressAction stressAction = new StressAction(settings, logout);
                stressAction.run();
                logout.flush();
                if (settings.graph.inGraphMode())
                    new StressGraph(settings, arguments).generateGraph();
            }

        }
        catch (Throwable t)
        {
            t.printStackTrace();
            return 1;
        }

        return 0;
    }

    public static void printHelpMessage()
    {
        StressSettings.printHelp();
    }

    private static class ShutDown extends Thread
    {
        private final Socket socket;
        private final ObjectOutputStream out;

        public ShutDown(Socket socket, ObjectOutputStream out)
        {
            this.out = out;
            this.socket = socket;
        }

        public void run()
        {
            try
            {
                if (!socket.isClosed())
                {
                    System.out.println("Control-C caught. Canceling running action and shutting down...");

                    out.writeInt(1);
                    out.close();

                    stopped = true;
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private static String threadDump(boolean lockedMonitors, boolean lockedSynchronizers) {
        StringBuilder threadDump = new StringBuilder(System.lineSeparator());
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for(ThreadInfo threadInfo : threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers)) {
            threadDump.append(threadInfo.toString());
        }
        return threadDump.toString();
    }

    private static void registerSignalHandler() {
        SignalHandler handler = signal -> {
            System.out.println("Caught signal: " + signal.getName());
            System.out.println(threadDump(true, true));
            System.exit(switch (signal.getName()) {
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
