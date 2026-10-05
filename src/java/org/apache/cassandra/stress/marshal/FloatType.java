// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

public final class FloatType extends AbstractType<Float> {
    public static final FloatType instance = new FloatType();

    private FloatType() {
        super(false);
    }

    @Override
    public TypeSerializer<Float> getSerializer() {
        return FloatSerializer.instance;
    }

    @Override
    protected int compareCustom(ByteBuffer o1, ByteBuffer o2) {
        if (!o1.hasRemaining() || !o2.hasRemaining()) return o1.hasRemaining() ? 1 : o2.hasRemaining() ? -1 : 0;

        return compose(o1).compareTo(compose(o2));
    }
}
