// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UTFDataFormatException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.cassandra.stress.settings.StressSettings;
import org.apache.cassandra.stress.util.HostAndPort;
import org.apache.cassandra.stress.util.MultiResultLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StressServer {
    private StressServer() {}

    public static final int DEFAULT_PORT = 2159;

    private static final Logger LOGGER = LoggerFactory.getLogger(StressServer.class);

    static final int MAX_ARGUMENTS = 1024;

    static final int MAX_CLIENTS = 16;

    static final int MAX_COMMAND_BYTES = 1 << 20;

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private static final long CANCEL_GRACE_MILLIS = 5_000;

    private static final Duration CLOSE_GRACE = Duration.ofSeconds(1);

    static final Semaphore CLIENTS = new Semaphore(MAX_CLIENTS);

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
            System.err.printf(
                    Locale.ROOT, "Could not listen on port: %s:%d.%n", address.getHostAddress(), listen.port());
            System.exit(1);
            return;
        }

        try (serverSocket) {
            for (; ; ) {
                accept(serverSocket.accept());
            }
        }
    }

    static void accept(Socket socket) throws IOException {
        if (!CLIENTS.tryAcquire()) {
            try (socket;
                    PrintStream out = new PrintStream(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                out.println("stressd serves " + MAX_CLIENTS + " clients at a time. Try again later.");
                out.println("FAILURE");
            }
            return;
        }
        new StressThread(socket).start();
    }

    static HostAndPort listenAddress(String[] args) {
        String host = "127.0.0.1";
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-h".equals(arg) || "--host".equals(arg)) {
                if (i + 1 == args.length) {
                    return null;
                }
                host = args[++i];
            } else if (arg.startsWith("--host=")) {
                host = arg.substring("--host=".length());
            } else if ("-p".equals(arg) || "--port".equals(arg)) {
                if (i + 1 == args.length) {
                    return null;
                }
                port = HostAndPort.parsePort(args[++i]);
            } else if (arg.startsWith("--port=")) {
                port = HostAndPort.parsePort(arg.substring("--port=".length()));
            } else if (arg.startsWith("-")) {
                return null;
            }
        }
        return new HostAndPort(host, port);
    }

    static void writeCommand(DataOutputStream out, String[] arguments) throws IOException {
        out.writeInt(arguments.length);
        for (String argument : arguments) {
            out.writeUTF(argument);
        }
        out.flush();
    }

    static String[] readCommand(DataInputStream in) throws IOException {
        try {
            int count = in.readInt();
            if (count < 0 || count > MAX_ARGUMENTS) {
                throw new IOException("Invalid argument count: " + count);
            }
            String[] arguments = new String[count];
            for (int i = 0; i < count; i++) {
                try {
                    arguments[i] = in.readUTF();
                } catch (UTFDataFormatException e) {
                    throw new IOException("Argument " + (i + 1) + " is not valid modified UTF-8", e);
                }
            }
            return arguments;
        } catch (EOFException e) {
            throw new IOException("The command ended before its last argument", e);
        }
    }

    static String failureMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    static void serve(Socket socket) throws IOException, InterruptedException {
        serve(socket, COMMAND_TIMEOUT);
    }

    @SuppressWarnings("PMD.CloseResource")
    static void serve(Socket socket, Duration commandTimeout) throws IOException, InterruptedException {
        CommandInput command = new CommandInput(socket, commandTimeout);
        try (socket;
                DataInputStream in = new DataInputStream(command);
                PrintStream out = new PrintStream(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            StressSettings settings;
            try {
                settings = StressSettings.parseForDaemon(readCommand(in));
                command.finish();
            } catch (RuntimeException | IOException e) {
                out.println(failureMessage(e));
                out.println("FAILURE");
                command.closeAfterReply();
                return;
            }
            if (settings == null) {
                out.println("FAILURE");
                return;
            }

            MultiResultLogger log = new MultiResultLogger(out);
            settings.setOutput(log);
            Thread actionThread = Thread.ofPlatform()
                    .name("stress-" + THREAD_COUNTER.incrementAndGet())
                    .start(new StressAction(settings, log));
            Thread canceller = Thread.ofVirtual().start(() -> cancelOnRequest(in, actionThread));
            try {
                actionThread.join();
                canceller.join(CANCEL_GRACE_MILLIS);
            } finally {
                settings.graph.deleteTemporaryLogFile();
            }
        }
    }

    private static void cancelOnRequest(DataInputStream in, Thread actionThread) {
        try {
            int signal;
            do {
                signal = in.readInt();
            } while (signal != 1);
        } catch (IOException e) {
            LOGGER.debug("The client closed the connection", e);
        }
        actionThread.interrupt();
    }

    static final class CommandInput extends FilterInputStream {
        private final Socket socket;
        private final Duration timeout;
        private final long deadlineNanos;
        private long bytesLeft = MAX_COMMAND_BYTES;
        private boolean limited = true;

        CommandInput(Socket socket, Duration timeout) throws IOException {
            super(socket.getInputStream());
            this.socket = socket;
            this.timeout = timeout;
            this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        }

        void finish() throws IOException {
            limited = false;
            socket.setSoTimeout(0);
        }

        void closeAfterReply() {
            limited = false;
            byte[] discard = new byte[8192];
            try {
                socket.shutdownOutput();
                socket.setSoTimeout((int) CLOSE_GRACE.toMillis());
                long deadline = System.nanoTime() + CLOSE_GRACE.toNanos();
                int read = 0;
                while (read >= 0 && System.nanoTime() < deadline) {
                    read = in.read(discard);
                }
            } catch (IOException e) {
                LOGGER.debug("The client closed the connection after the reply", e);
            }
        }

        private int allowed(int length) throws IOException {
            if (!limited) {
                return length;
            }
            if (bytesLeft == 0) {
                throw new IOException("The command is longer than " + MAX_COMMAND_BYTES + " bytes");
            }
            long left = deadlineNanos - System.nanoTime();
            if (left <= 0) {
                throw timedOut(null);
            }
            socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(left)));
            return (int) Math.min(length, bytesLeft);
        }

        private SocketTimeoutException timedOut(Throwable cause) {
            SocketTimeoutException e =
                    new SocketTimeoutException("The command took longer than " + timeout.toMillis() + " ms");
            e.initCause(cause);
            return e;
        }

        @Override
        public int read() throws IOException {
            allowed(1);
            try {
                int b = super.read();
                if (limited && b >= 0) {
                    bytesLeft--;
                }
                return b;
            } catch (SocketTimeoutException e) {
                throw limited ? timedOut(e) : e;
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            int allowed = allowed(length);
            try {
                int read = super.read(buffer, offset, allowed);
                if (limited && read > 0) {
                    bytesLeft -= read;
                }
                return read;
            } catch (SocketTimeoutException e) {
                throw limited ? timedOut(e) : e;
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
            } finally {
                CLIENTS.release();
            }
        }
    }
}
