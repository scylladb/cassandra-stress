// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Locale;
import org.apache.cassandra.stress.util.ByteBufferUtil;

public class InetAddressSerializer implements TypeSerializer<InetAddress> {
    public static final InetAddressSerializer instance = new InetAddressSerializer();

    @Override
    public InetAddress deserialize(ByteBuffer bytes) {
        if (bytes.remaining() == 0) return null;

        try {
            return InetAddress.getByAddress(ByteBufferUtil.getArray(bytes));
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public ByteBuffer serialize(InetAddress value) {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : ByteBuffer.wrap(value.getAddress());
    }

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        if (bytes.remaining() == 0) return;

        try {
            InetAddress.getByAddress(ByteBufferUtil.getArray(bytes));
        } catch (UnknownHostException e) {
            throw new MarshalException(
                    String.format(
                            Locale.ROOT, "Expected 4 or 16 byte inetaddress; got %s", ByteBufferUtil.bytesToHex(bytes)),
                    e);
        }
    }

    @Override
    public String toString(InetAddress value) {
        return value == null ? "" : value.getHostAddress();
    }

    @Override
    public Class<InetAddress> getType() {
        return InetAddress.class;
    }
}
