// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.Comparator;

import org.apache.cassandra.stress.util.ByteBufferUtil;

public abstract class AbstractType<T> implements Comparator<ByteBuffer>
{
    private final boolean byteOrderComparable;

    protected AbstractType(boolean byteOrderComparable)
    {
        this.byteOrderComparable = byteOrderComparable;
    }

    public abstract TypeSerializer<T> getSerializer();

    public T compose(ByteBuffer bytes)
    {
        return getSerializer().deserialize(bytes);
    }

    public ByteBuffer decompose(T value)
    {
        return getSerializer().serialize(value);
    }

    public String getString(ByteBuffer bytes)
    {
        if (bytes == null)
            return "null";

        TypeSerializer<T> serializer = getSerializer();
        serializer.validate(bytes);
        return serializer.toString(serializer.deserialize(bytes));
    }

    public final int compare(ByteBuffer left, ByteBuffer right)
    {
        return byteOrderComparable ? ByteBufferUtil.compareUnsigned(left, right) : compareCustom(left, right);
    }

    protected int compareCustom(ByteBuffer left, ByteBuffer right)
    {
        throw new UnsupportedOperationException();
    }

    static int compareSignedFirstByte(ByteBuffer o1, ByteBuffer o2)
    {
        if (!o1.hasRemaining() || !o2.hasRemaining())
            return o1.hasRemaining() ? 1 : o2.hasRemaining() ? -1 : 0;

        int diff = o1.get(o1.position()) - o2.get(o2.position());
        if (diff != 0)
            return diff;

        return ByteBufferUtil.compareUnsigned(o1, o2);
    }
}
