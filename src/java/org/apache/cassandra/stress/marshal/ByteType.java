// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

public final class ByteType extends AbstractType<Byte> {
    public static final ByteType instance = new ByteType();

    private ByteType() {
        super(false);
    }

    @Override
    public TypeSerializer<Byte> getSerializer() {
        return ByteSerializer.instance;
    }

    @Override
    protected int compareCustom(ByteBuffer o1, ByteBuffer o2) {
        return o1.get(o1.position()) - o2.get(o2.position());
    }
}
