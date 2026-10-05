package org.apache.cassandra.stress.util;

import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;

@FunctionalInterface
public interface JavaDriverV4ConfigBuilder {
    ProgrammaticDriverConfigLoaderBuilder applyConfig(ProgrammaticDriverConfigLoaderBuilder builder);
}
