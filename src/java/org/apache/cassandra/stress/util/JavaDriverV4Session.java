package org.apache.cassandra.stress.util;

import shaded.com.datastax.oss.driver.api.core.CqlSessionBuilder;

public interface JavaDriverV4Session {
  CqlSessionBuilder apply(CqlSessionBuilder builder);
}

