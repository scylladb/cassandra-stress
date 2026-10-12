// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

public enum ProtocolCompression {
    NONE(""),
    SNAPPY("snappy"),
    LZ4("lz4");

    private final String name;

    ProtocolCompression(String name) {
        this.name = name;
    }

    public String protocolName() {
        return name;
    }
}
