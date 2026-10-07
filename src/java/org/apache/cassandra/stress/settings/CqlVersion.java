// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

public enum CqlVersion {
    NOCQL(null),
    CQL3("3.0.0");

    public final String connectVersion;

    CqlVersion(String connectVersion) {
        this.connectVersion = connectVersion;
    }

    static CqlVersion get(String version) {
        if (version == null) {
            return NOCQL;
        }
        if (version.charAt(0) != '3') {
            throw new IllegalStateException();
        }
        return CQL3;
    }

    public boolean isCql() {
        return this != NOCQL;
    }

    public boolean isCql3() {
        return this == CQL3;
    }
}
