package com.scylladb.stress.util;

import com.scylladb.utils.ConsistencyLevel;

public interface QueryExecutor {
  void execute(String query, ConsistencyLevel consistency);
}
