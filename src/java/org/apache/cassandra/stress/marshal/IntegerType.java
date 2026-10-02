// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public final class IntegerType extends AbstractType<BigInteger>
{
    public static final IntegerType instance = new IntegerType();

    private IntegerType()
    {
        super(false);
    }

    public TypeSerializer<BigInteger> getSerializer()
    {
        return IntegerSerializer.instance;
    }

    protected int compareCustom(ByteBuffer o1, ByteBuffer o2)
    {
        int lhsLen = o1.remaining();
        int rhsLen = o2.remaining();

        if (lhsLen == 0)
            return rhsLen == 0 ? 0 : -1;
        if (rhsLen == 0)
            return 1;

        int lhsMsbIdx = findMostSignificantByte(o1);
        int rhsMsbIdx = findMostSignificantByte(o2);

        int lhsLenDiff = lhsLen - lhsMsbIdx;
        int rhsLenDiff = rhsLen - rhsMsbIdx;

        byte lhsMsb = o1.get(o1.position() + lhsMsbIdx);
        byte rhsMsb = o2.get(o2.position() + rhsMsbIdx);

        if (lhsLenDiff != rhsLenDiff)
        {
            if (lhsMsb < 0)
                return rhsMsb < 0 ? rhsLenDiff - lhsLenDiff : -1;
            if (rhsMsb < 0)
                return 1;
            return lhsLenDiff - rhsLenDiff;
        }

        if (lhsMsb != rhsMsb)
            return lhsMsb - rhsMsb;
        lhsMsbIdx++;
        rhsMsbIdx++;

        while (lhsMsbIdx < lhsLen)
        {
            lhsMsb = o1.get(o1.position() + lhsMsbIdx++);
            rhsMsb = o2.get(o2.position() + rhsMsbIdx++);

            if (lhsMsb != rhsMsb)
                return (lhsMsb & 0xFF) - (rhsMsb & 0xFF);
        }

        return 0;
    }

    private static int findMostSignificantByte(ByteBuffer bytes)
    {
        int len = bytes.remaining() - 1;
        int i = 0;
        for (; i < len; i++)
        {
            byte b0 = bytes.get(bytes.position() + i);
            if (b0 != 0 && b0 != -1)
                break;
            byte b1 = bytes.get(bytes.position() + i + 1);
            if (b0 == 0 && b1 != 0)
            {
                if (b1 > 0)
                    i++;
                break;
            }
            if (b0 == -1 && b1 != -1)
            {
                if (b1 < 0)
                    i++;
                break;
            }
        }
        return i;
    }
}
