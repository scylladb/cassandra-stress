// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.apache.cassandra.stress.util.ResultLogger;

public final class StressServer {
    private StressServer() {}

    static final int MAX_ARGUMENTS = 1024;

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    @SuppressWarnings("PMD.CloseResource")
    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = null;
        String host = listenHost(args);
        if (host == null) {
            System.err.println("Usage: ./bin/stressd start|stop|status [-h <host>]");
            System.exit(1);
        }
        InetAddress address = InetAddress.getByName(host);

        try {
            serverSocket = new ServerSocket(2159, 0, address);
        } catch (IOException e) {
            System.err.printf("Could not listen on port: %s:2159.%n", address.getHostAddress());
            System.exit(1);
        }

        for (; ; ) new StressThread(serverSocket.accept()).start();
    }

    static String listenHost(String[] args) {
        String host = "127.0.0.1";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-h".equals(arg) || "--host".equals(arg)) {
                if (i + 1 == args.length) return null;
                host = args[++i];
            } else if (arg.startsWith("--host=")) host = arg.substring("--host=".length());
            else if (arg.startsWith("-")) return null;
        }
        return host;
    }

    static void writeCommand(DataOutputStream out, String[] arguments) throws IOException {
        out.writeInt(arguments.length);
        for (String argument : arguments) out.writeUTF(argument);
        out.flush();
    }

    static String[] readCommand(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_ARGUMENTS) throw new IOException("Invalid argument count: " + count);
        String[] arguments = new String[count];
        for (int i = 0; i < count; i++) arguments[i] = in.readUTF();
        return arguments;
    }

    public static class StressThread extends Thread {
        private final Socket socket;

        public StressThread(Socket client) {
            this.socket = client;
        }

        @SuppressWarnings({"CatchAndPrintStackTrace", "PMD.CloseResource"})
        @Override
        public void run() {
            try {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                PrintStream out = new PrintStream(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                ResultLogger log = new MultiResultLogger(out);

                StressSettings settings;
                try {
                    settings = StressSettings.parse(readCommand(in));
                } catch (IllegalArgumentException e) {
                    out.println(e.getMessage());
                    out.println("FAILURE");
                    socket.close();
                    return;
                }
                if (settings == null) {
                    out.println("FAILURE");
                    socket.close();
                    return;
                }

                StressAction action = new StressAction(settings, log);
                Thread actionThread = Thread.ofPlatform()
                        .name("stress-" + THREAD_COUNTER.incrementAndGet())
                        .start(action);

                while (actionThread.isAlive()) {
                    try {
                        if (in.readInt() == 1) {
                            actionThread.interrupt();
                            break;
                        }
                    } catch (IOException e) {
                        actionThread.join();
                        break;
                    }
                }

                out.close();
                in.close();
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
