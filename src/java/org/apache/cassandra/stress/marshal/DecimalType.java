// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

public final class DecimalType extends AbstractType<BigDecimal>
{
    public static final DecimalType instance = new DecimalType();

    private DecimalType()
    {
        super(false);
    }

    public TypeSerializer<BigDecimal> getSerializer()
    {
        return DecimalSerializer.instance;
    }

    protected int compareCustom(ByteBuffer o1, ByteBuffer o2)
    {
        if (!o1.hasRemaining() || !o2.hasRemaining())
            return o1.hasRemaining() ? 1 : o2.hasRemaining() ? -1 : 0;

        return compose(o1).compareTo(compose(o2));
    }
}
