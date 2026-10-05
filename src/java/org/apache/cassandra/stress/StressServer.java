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
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.apache.cassandra.stress.util.ResultLogger;

public final class StressServer {
    private StressServer() {}

    public static final int DEFAULT_PORT = 2159;

    static final int MAX_ARGUMENTS = 1024;

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    public static void main(String[] args) throws IOException {
        HostAndPort listen;
        try {
            listen = listenAddress(args);
        } catch (IllegalArgumentException e) {
            listen = null;
        }
        if (listen == null) {
            System.err.println("Usage: ./bin/stressd start|stop|status [-h <host>] [-p <port>]");
            System.exit(1);
        }
        InetAddress address = InetAddress.getByName(listen.host());

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(listen.port(), 0, address);
        } catch (IOException e) {
            System.err.printf("Could not listen on port: %s:%d.%n", address.getHostAddress(), listen.port());
            System.exit(1);
            return;
        }

        try (serverSocket) {
            for (; ; ) new StressThread(serverSocket.accept()).start();
        }
    }

    static HostAndPort listenAddress(String[] args) {
        String host = "127.0.0.1";
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-h".equals(arg) || "--host".equals(arg)) {
                if (i + 1 == args.length) return null;
                host = args[++i];
            } else if (arg.startsWith("--host=")) host = arg.substring("--host=".length());
            else if ("-p".equals(arg) || "--port".equals(arg)) {
                if (i + 1 == args.length) return null;
                port = HostAndPort.parsePort(args[++i]);
            } else if (arg.startsWith("--port=")) port = HostAndPort.parsePort(arg.substring("--port=".length()));
            else if (arg.startsWith("-")) return null;
        }
        return new HostAndPort(host, port);
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

    static String failureMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    static void serve(Socket socket) throws IOException, InterruptedException {
        try (socket;
                DataInputStream in = new DataInputStream(socket.getInputStream());
                PrintStream out = new PrintStream(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            StressSettings settings;
            try {
                settings = StressSettings.parse(readCommand(in));
            } catch (IllegalArgumentException e) {
                out.println(failureMessage(e));
                out.println("FAILURE");
                return;
            }
            if (settings == null) {
                out.println("FAILURE");
                return;
            }

            ResultLogger log = new MultiResultLogger(out);
            settings.setOutput(log);
            Thread actionThread = Thread.ofPlatform()
                    .name("stress-" + THREAD_COUNTER.incrementAndGet())
                    .start(new StressAction(settings, log));

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
        }
    }

    public static class StressThread extends Thread {
        private final Socket socket;

        public StressThread(Socket client) {
            this.socket = client;
        }

        @SuppressWarnings("CatchAndPrintStackTrace")
        @Override
        public void run() {
            try {
                serve(socket);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
