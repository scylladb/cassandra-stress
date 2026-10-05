// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.net.InetAddress;

public final class InetAddressType extends AbstractType<InetAddress> {
    public static final InetAddressType instance = new InetAddressType();

    private InetAddressType() {
        super(true);
    }

    @Override
    public TypeSerializer<InetAddress> getSerializer() {
        return InetAddressSerializer.instance;
    }
}
