package org.apache.cassandra.stress.util;

import org.apache.cassandra.stress.core.PreparedStatement;

public interface QueryPrepare {
  public PreparedStatement prepare(String query);
}
