package org.apache.cassandra.stress.util;

import shaded.com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;

public interface JavaDriverV4Config {
  ProgrammaticDriverConfigLoaderBuilder applyConfig(ProgrammaticDriverConfigLoaderBuilder builder);
}

