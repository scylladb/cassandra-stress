package com.scylladb.io;

import java.io.IOException;

public class FSException extends Exception {
    public FSException(IOException exception, String directory) {
        super(exception);
    }
}
