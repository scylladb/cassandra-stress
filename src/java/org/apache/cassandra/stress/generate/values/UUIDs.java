// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.util.UUID;
import org.apache.cassandra.stress.marshal.UUIDType;

public class UUIDs extends Generator<UUID> {
    public UUIDs(String name, GeneratorConfig config) {
        super(UUIDType.instance, config, name, UUID.class);
    }

    @Override
    public UUID generate() {
        long seed = identityDistribution.next();
        return new UUID(seed, seed);
    }
}
