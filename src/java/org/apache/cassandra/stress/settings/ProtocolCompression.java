// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;

public enum ProtocolCompression {
    NONE(""),
    SNAPPY("snappy"),
    LZ4("lz4");

    private final String name;

    ProtocolCompression(String name) {
        this.name = name;
    }

    public ProgrammaticDriverConfigLoaderBuilder applyTo(ProgrammaticDriverConfigLoaderBuilder builder) {
        if (name.isEmpty()) return builder;
        return builder.withString(DefaultDriverOption.PROTOCOL_COMPRESSION, name);
    }
}
