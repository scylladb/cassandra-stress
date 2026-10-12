// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public record StressResult(List<String> columnNames, List<ByteBuffer[]> rows) {
    public static final StressResult EMPTY = new StressResult(List.of(), List.of());

    public StressResult {
        columnNames = Collections.unmodifiableList(columnNames);
        rows = Collections.unmodifiableList(rows);
    }
}
