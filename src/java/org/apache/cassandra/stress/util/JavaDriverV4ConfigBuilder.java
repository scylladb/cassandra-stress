package org.apache.cassandra.stress.util;

import shaded.com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;

public interface JavaDriverV4ConfigBuilder {
  ProgrammaticDriverConfigLoaderBuilder applyConfig(ProgrammaticDriverConfigLoaderBuilder builder);
}

