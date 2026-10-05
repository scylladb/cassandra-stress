// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

public final class DoubleType extends AbstractType<Double> {
    public static final DoubleType instance = new DoubleType();

    private DoubleType() {
        super(false);
    }

    @Override
    public TypeSerializer<Double> getSerializer() {
        return DoubleSerializer.instance;
    }

    @Override
    protected int compareCustom(ByteBuffer o1, ByteBuffer o2) {
        if (!o1.hasRemaining() || !o2.hasRemaining()) return o1.hasRemaining() ? 1 : o2.hasRemaining() ? -1 : 0;

        return compose(o1).compareTo(compose(o2));
    }
}
