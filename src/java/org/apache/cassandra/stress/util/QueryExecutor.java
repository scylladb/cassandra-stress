package org.apache.cassandra.stress.util;

@FunctionalInterface
public interface QueryExecutor {
    void execute(String query, org.apache.cassandra.stress.util.ConsistencyLevel consistency);
}
