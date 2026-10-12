// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.util.List;

public record CqlType(String name, List<CqlType> parameters, boolean frozen) {
    public CqlType {
        parameters = List.copyOf(parameters);
    }

    public static CqlType of(String name) {
        return new CqlType(name, List.of(), false);
    }

    public String elementName() {
        return ("LIST".equals(name) || "SET".equals(name)) && !parameters.isEmpty()
                ? parameters.getFirst().name()
                : "";
    }
}
