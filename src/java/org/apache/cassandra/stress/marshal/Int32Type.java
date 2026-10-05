// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

public final class Int32Type extends AbstractType<Integer> {
    public static final Int32Type instance = new Int32Type();

    private Int32Type() {
        super(false);
    }

    @Override
    public TypeSerializer<Integer> getSerializer() {
        return Int32Serializer.instance;
    }

    @Override
    protected int compareCustom(ByteBuffer o1, ByteBuffer o2) {
        return compareSignedFirstByte(o1, o2);
    }
}
