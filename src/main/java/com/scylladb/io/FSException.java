package com.scylladb.io;

import java.io.IOException;

public class FSException extends RuntimeException {
    public FSException(IOException exception, String directory) {
        super(exception);
    }
}
