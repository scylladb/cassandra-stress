package com.scylladb.stress.generate.values;

import java.nio.ByteBuffer;

public interface CustomComparer {
    int customCompare(ByteBuffer o1, ByteBuffer o2);
}
