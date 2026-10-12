// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.net.InetSocketAddress;

public record HostAndPort(String host, int port) {
    public HostAndPort {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("The host is empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("The port must be in the range 1-65535: " + port);
        }
    }

    public static HostAndPort parse(String address, int defaultPort) {
        if (address.startsWith("[")) {
            int close = address.indexOf(']');
            if (close < 0) {
                throw new IllegalArgumentException("Invalid address: " + address);
            }
            String host = address.substring(1, close);
            String rest = address.substring(close + 1);
            if (rest.isEmpty()) {
                return new HostAndPort(host, defaultPort);
            }
            if (!rest.startsWith(":")) {
                throw new IllegalArgumentException("Invalid address: " + address);
            }
            return new HostAndPort(host, parsePort(rest.substring(1)));
        }
        int colon = address.indexOf(':');
        if (colon < 0 || colon != address.lastIndexOf(':')) {
            return new HostAndPort(address, defaultPort);
        }
        return new HostAndPort(address.substring(0, colon), parsePort(address.substring(colon + 1)));
    }

    public static int parsePort(String port) {
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: " + port, e);
        }
    }

    public InetSocketAddress toSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    @Override
    public String toString() {
        return (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + port;
    }
}
