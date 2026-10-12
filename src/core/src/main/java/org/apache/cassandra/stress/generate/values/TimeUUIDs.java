// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.util.UUID;
import org.apache.cassandra.stress.marshal.TimeUUIDType;
import org.apache.cassandra.stress.util.UUIDGen;

public class TimeUUIDs extends Generator<UUID> {
    final Dates dateGen;
    final long clockSeqAndNode;

    public TimeUUIDs(String name, GeneratorConfig config) {
        super(TimeUUIDType.instance, config, name, UUID.class);
        dateGen = new Dates(name, config);
        clockSeqAndNode = config.salt;
    }

    @Override
    public void setSeed(long seed) {
        dateGen.setSeed(seed);
    }

    @Override
    public UUID generate() {
        return UUIDGen.getTimeUUID(dateGen.generate().getTime(), 0L, clockSeqAndNode);
    }
}
