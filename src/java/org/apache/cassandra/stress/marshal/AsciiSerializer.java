// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class AsciiSerializer extends AbstractTextSerializer
{
    public static final AsciiSerializer instance = new AsciiSerializer();

    private AsciiSerializer()
    {
        super(StandardCharsets.US_ASCII);
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
        for (int i = bytes.position(); i < bytes.limit(); i++)
        {
            byte b = bytes.get(i);
            if (b < 0)
                throw new MarshalException("Invalid byte for ascii: " + Byte.toString(b));
        }
    }
}
