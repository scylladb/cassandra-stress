package org.apache.cassandra.stress.settings;

public final class ProtocolVersion {
    int protocolVersion;

    private ProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    static final ProtocolVersion DEFAULT = new ProtocolVersion(SpecialVersions.DEFAULT.index);
    static final ProtocolVersion NEWEST_SUPPORTED = new ProtocolVersion(SpecialVersions.NEWEST_SUPPORTED.index);

    public static ProtocolVersion fromInt(int i) {
        return new ProtocolVersion(i);
    }

    public boolean isDefault() {
        return protocolVersion == SpecialVersions.DEFAULT.index;
    }

    public int number() {
        if (protocolVersion == SpecialVersions.NEWEST_SUPPORTED.index) return 5;
        if (protocolVersion <= 0) throw new IllegalArgumentException("Invalid protocol version: " + protocolVersion);
        return protocolVersion;
    }

    @Override
    public String toString() {
        if (protocolVersion <= 0) {
            if (protocolVersion == SpecialVersions.DEFAULT.index) {
                return "DEFAULT";
            } else if (protocolVersion == SpecialVersions.NEWEST_SUPPORTED.index) {
                return "NEWEST_SUPPORTED";
            }
            return String.format("unknown version: %d", protocolVersion);
        }
        return String.format("%d", protocolVersion);
    }

    private enum SpecialVersions {
        DEFAULT(-1),
        NEWEST_SUPPORTED(-3);

        private final int index;

        SpecialVersions(int index) {
            this.index = index;
        }
    }
}
