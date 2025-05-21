package org.apache.cassandra.stress.util;

import shaded.com.datastax.oss.driver.api.core.CqlSessionBuilder;

public interface JavaDriverV4SessionBuilder {
  CqlSessionBuilder apply(CqlSessionBuilder builder);
}

