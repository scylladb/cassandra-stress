package com.scylladb.stress.util;

import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;

public interface JavaDriverV4ConfigBuilder {
    ProgrammaticDriverConfigLoaderBuilder applyConfig(ProgrammaticDriverConfigLoaderBuilder builder);
}
