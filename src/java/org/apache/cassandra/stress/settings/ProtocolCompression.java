package org.apache.cassandra.stress.settings;

import java.util.Locale;

import org.apache.cassandra.stress.util.JavaDriverV4ConfigBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;

public enum ProtocolCompression {
  NONE(""),
  SNAPPY("snappy"),
  LZ4("lz4");

  private final String name;

  ProtocolCompression(String name) {
    this.name = name;
  }

  public String Name() {
    return name;
  }

  public com.datastax.driver.core.ProtocolOptions.Compression ToJavaDriverV3() {
    if (name.isEmpty()) {
      return com.datastax.driver.core.ProtocolOptions.Compression.NONE;
    }
    return com.datastax.driver.core.ProtocolOptions.Compression.valueOf(name.toUpperCase(Locale.ROOT));
  }

  public JavaDriverV4ConfigBuilder ToJavaDriverV4() {
    return new JavaDriverV4ConfigBuilder() {
      @Override
      public ProgrammaticDriverConfigLoaderBuilder applyConfig(ProgrammaticDriverConfigLoaderBuilder builder) {
        if (name.isEmpty()) {
          return builder;
        }
        return builder.withString(DefaultDriverOption.PROTOCOL_COMPRESSION, name);
      }
    };
  }
}
