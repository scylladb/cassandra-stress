// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

public final class BytesType extends AbstractType<ByteBuffer>
{
    public static final BytesType instance = new BytesType();

    private BytesType()
    {
        super(true);
    }

    public TypeSerializer<ByteBuffer> getSerializer()
    {
        return BytesSerializer.instance;
    }
}
