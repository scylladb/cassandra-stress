package com.scylladb.stress.util;

import com.scylladb.stress.core.PreparedStatement;

public interface QueryPrepare {
    PreparedStatement prepare(String query);
}
