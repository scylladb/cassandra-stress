// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.cassandra.stress.marshal.InetAddressType;

public class Inets extends Generator<InetAddress>
{
    final byte[] buf;
    public Inets(String name, GeneratorConfig config)
    {
        super(InetAddressType.instance, config, name, InetAddress.class);
        buf = new byte[4];
    }

    @Override
    public InetAddress generate()
    {
        int val = (int) identityDistribution.next();

        buf[0] = (byte)(val >>> 24);
        buf[1] = (byte)(val >>> 16);
        buf[2] = (byte)(val >>> 8);
        buf[3] = (byte)val;

        try
        {
            return InetAddress.getByAddress(buf);
        }
        catch (UnknownHostException e)
        {
            throw new RuntimeException(e);
        }
    }
}
