package com.scylladb.stress.generate.values;

public enum ComparisonType {
    /**
     * This type should never be compared
     */
    NOT_COMPARABLE,
    /**
     * This type is always compared by its sequence of unsigned bytes
     */
    BYTE_ORDER,
    /**
     * This type can only be compared by calling the type's compareCustom() method, which may be expensive.
     * Support for this may be removed in a major release of Cassandra, however upgrade facilities will be
     * provided if and when this happens.
     */
    CUSTOM
}
