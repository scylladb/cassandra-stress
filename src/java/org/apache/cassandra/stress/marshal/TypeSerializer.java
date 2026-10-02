// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

public interface TypeSerializer<T>
{
    public ByteBuffer serialize(T value);

    public T deserialize(ByteBuffer bytes);

    public void validate(ByteBuffer bytes) throws MarshalException;

    public String toString(T value);

    public Class<T> getType();

    public default String toCQLLiteral(ByteBuffer buffer)
    {
        return buffer == null || !buffer.hasRemaining()
             ? "null"
             : toString(deserialize(buffer));
    }
}
