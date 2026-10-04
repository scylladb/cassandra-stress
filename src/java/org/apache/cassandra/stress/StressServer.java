// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.apache.cassandra.stress.util.ResultLogger;

public class StressServer
{
    static final ObjectInputFilter SETTINGS_FILTER = ObjectInputFilter.Config.createFilter(
        "maxdepth=64;org.apache.cassandra.stress.**;java.lang.*;java.util.*;java.util.concurrent.TimeUnit;java.util.regex.Pattern;java.io.File;!*");

    private static final AtomicInteger threadCounter = new AtomicInteger(1);

    public static void main(String[] args) throws Exception
    {
        ServerSocket serverSocket = null;
        String host = listenHost(args);
        if (host == null)
        {
            System.err.println("Usage: ./bin/stressd start|stop|status [-h <host>]");
            System.exit(1);
        }
        InetAddress address = InetAddress.getByName(host);

        try
        {
            serverSocket = new ServerSocket(2159, 0, address);
        }
        catch (IOException e)
        {
            System.err.printf("Could not listen on port: %s:2159.%n", address.getHostAddress());
            System.exit(1);
        }

        for (;;)
            new StressThread(serverSocket.accept()).start();
    }

    static String listenHost(String[] args)
    {
        String host = "127.0.0.1";
        for (int i = 0; i < args.length; i++)
        {
            String arg = args[i];
            if (arg.equals("-h") || arg.equals("--host"))
            {
                if (i + 1 == args.length)
                    return null;
                host = args[++i];
            }
            else if (arg.startsWith("--host="))
                host = arg.substring("--host=".length());
            else if (arg.startsWith("-"))
                return null;
        }
        return host;
    }

    public static class StressThread extends Thread
    {
        private final Socket socket;

        public StressThread(Socket client)
        {
            this.socket = client;
        }

        public void run()
        {
            try
            {
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                in.setObjectInputFilter(SETTINGS_FILTER);
                PrintStream out = new PrintStream(socket.getOutputStream());
                ResultLogger log = new MultiResultLogger(out);

                StressAction action = new StressAction((StressSettings) in.readObject(), log);
                Thread actionThread = Thread.ofPlatform().name("stress-" + threadCounter.incrementAndGet()).start(action);

                while (actionThread.isAlive())
                {
                    try
                    {
                        if (in.readInt() == 1)
                        {
                            actionThread.interrupt();
                            break;
                        }
                    }
                    catch (Exception e)
                    {
                    }
                }

                out.close();
                in.close();
                socket.close();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e.getMessage(), e);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

    }

}
