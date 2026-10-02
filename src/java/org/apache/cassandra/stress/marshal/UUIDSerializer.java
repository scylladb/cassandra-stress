// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.apache.cassandra.stress.util.ByteBufferUtil;
import org.apache.cassandra.stress.util.UUIDGen;

public class UUIDSerializer implements TypeSerializer<UUID>
{
    public static final UUIDSerializer instance = new UUIDSerializer();

    public UUID deserialize(ByteBuffer bytes)
    {
        return bytes.remaining() == 0 ? null : UUIDGen.getUUID(bytes);
    }

    public ByteBuffer serialize(UUID value)
    {
        return value == null ? ByteBufferUtil.EMPTY_BYTE_BUFFER : UUIDGen.toByteBuffer(value);
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
        if (bytes.remaining() != 16 && bytes.remaining() != 0)
            throw new MarshalException(String.format("UUID should be 16 or 0 bytes (%d)", bytes.remaining()));
    }

    public String toString(UUID value)
    {
        return value == null ? "" : value.toString();
    }

    public Class<UUID> getType()
    {
        return UUID.class;
    }
}
