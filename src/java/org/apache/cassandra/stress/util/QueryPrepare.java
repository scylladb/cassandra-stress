package org.apache.cassandra.stress.util;

import org.apache.cassandra.stress.core.PreparedStatement;

@FunctionalInterface
public interface QueryPrepare {
    PreparedStatement prepare(String query);
}
