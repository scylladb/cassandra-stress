package org.apache.cassandra.stress.settings;

public class ProtocolVersion {
  int protocolVersion;

  private ProtocolVersion(int protocolVersion) {
    this.protocolVersion = protocolVersion;
  }

  final static ProtocolVersion DEFAULT = new ProtocolVersion(SpecialVersions.DEFAULT.index);
  final static ProtocolVersion NEWEST_BETA = new ProtocolVersion(SpecialVersions.NEWEST_BETA.index);
  final static ProtocolVersion NEWEST_SUPPORTED = new ProtocolVersion(SpecialVersions.NEWEST_SUPPORTED.index);

  public static ProtocolVersion fromInt(int i) {
    return new ProtocolVersion(i);
  }

  public com.datastax.driver.core.ProtocolVersion ToV3() {
    if (protocolVersion == SpecialVersions.DEFAULT.index) {
      return com.datastax.driver.core.ProtocolVersion.DEFAULT;
    } else if (protocolVersion == SpecialVersions.NEWEST_BETA.index) {
      return com.datastax.driver.core.ProtocolVersion.NEWEST_BETA;
    } else if (protocolVersion == SpecialVersions.NEWEST_SUPPORTED.index) {
      return com.datastax.driver.core.ProtocolVersion.NEWEST_SUPPORTED;
    } else if (protocolVersion <= 0) {
      throw new IllegalArgumentException("Invalid protocol version: " + protocolVersion);
    }
    return com.datastax.driver.core.ProtocolVersion.fromInt(protocolVersion);
  }

  public shaded.com.datastax.oss.driver.api.core.ProtocolVersion ToV4() {
    if (protocolVersion == SpecialVersions.DEFAULT.index) {
      // Driver 4.x does not downgrade if protocol is set
      // and fails after first retry if server does not support provided protocol
      // So, best default is no protocol
      return null;
    } else if (protocolVersion == SpecialVersions.NEWEST_BETA.index) {
      return shaded.com.datastax.oss.driver.api.core.ProtocolVersion.DSE_V2;
    } else if (protocolVersion == SpecialVersions.NEWEST_SUPPORTED.index) {
      return shaded.com.datastax.oss.driver.api.core.ProtocolVersion.V6;
    } else if (protocolVersion <= 0) {
      throw new IllegalArgumentException("Invalid protocol version: " + protocolVersion);
    }

    switch (protocolVersion) {
      case 3:
        return shaded.com.datastax.oss.driver.api.core.ProtocolVersion.V3;
      case 4:
        return shaded.com.datastax.oss.driver.api.core.ProtocolVersion.V4;
      case 5:
        return shaded.com.datastax.oss.driver.api.core.ProtocolVersion.V5;
      case 6:
        return shaded.com.datastax.oss.driver.api.core.ProtocolVersion.V6;
      case 65:
        return shaded.com.datastax.oss.driver.api.core.ProtocolVersion.DSE_V1;
      case 66:
        return shaded.com.datastax.oss.driver.api.core.ProtocolVersion.DSE_V2;
      default:
        throw new IllegalArgumentException("Invalid protocol version: " + protocolVersion);
    }
  }

  @Override
  public String toString() {
    if (protocolVersion <= 0) {
      if (protocolVersion == SpecialVersions.DEFAULT.index) {
        return "DEFAULT";
      } else if (protocolVersion == SpecialVersions.NEWEST_BETA.index) {
        return "NEWEST_BETA";
      } else if (protocolVersion == SpecialVersions.NEWEST_SUPPORTED.index) {
        return "NEWEST_SUPPORTED";
      }
      return String.format("unknown version: %d", protocolVersion);
    }
    return String.format("%d", protocolVersion);
  }

  private static enum SpecialVersions {
    DEFAULT(-1),
    NEWEST_BETA(-2),
    NEWEST_SUPPORTED(-3);

    private final int index;

    SpecialVersions(int index) {
      this.index = index;
    }

    public int index() {
      return index;
    }
  }
}
