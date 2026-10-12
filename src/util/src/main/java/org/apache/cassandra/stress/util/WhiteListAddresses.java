// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class WhiteListAddresses {
    private WhiteListAddresses() {}

    public static Set<InetAddress> resolve(List<HostAndPort> contactPoints) {
        return contactPoints.stream().map(WhiteListAddresses::resolve).collect(Collectors.toUnmodifiableSet());
    }

    private static InetAddress resolve(HostAndPort contactPoint) {
        InetAddress address = contactPoint.toSocketAddress().getAddress();
        if (address == null) {
            throw new IllegalArgumentException("Cannot resolve the whitelisted node " + contactPoint.host());
        }
        return address;
    }
}
