// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;

public class TimeUUIDSerializer extends UUIDSerializer {
    public static final TimeUUIDSerializer instance = new TimeUUIDSerializer();

    @Override
    public void validate(ByteBuffer bytes) throws MarshalException {
        super.validate(bytes);

        ByteBuffer slice = bytes.slice();

        if (bytes.remaining() > 0) {
            slice.position(6);
            if ((slice.get() & 0xf0) != 0x10) {
                throw new MarshalException("Invalid version for TimeUUID type.");
            }
        }
    }
}
