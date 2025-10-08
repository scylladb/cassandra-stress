package org.apache.cassandra.stress.util;

public interface QueryExecutor {
  void execute(String query, org.apache.cassandra.db.ConsistencyLevel consistency);
}
