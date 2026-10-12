// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

public interface TypeSerializer<T> {
    ByteBuffer serialize(T value);

    T deserialize(ByteBuffer bytes);

    void validate(ByteBuffer bytes) throws MarshalException;

    String toString(T value);

    Class<T> getType();

    default String toCQLLiteral(ByteBuffer buffer) {
        return buffer == null || !buffer.hasRemaining() ? "null" : toString(deserialize(buffer));
    }
}
