// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import org.apache.cassandra.stress.marshal.TimeType;

public class Times extends Generator<Long> {
    public Times(String name, GeneratorConfig config) {
        super(TimeType.instance, config, name, Long.class);
    }

    @Override
    public Long generate() {
        return identityDistribution.next();
    }
}
