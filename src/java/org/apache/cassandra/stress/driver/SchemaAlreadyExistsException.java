// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.io.Serial;

public final class SchemaAlreadyExistsException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public SchemaAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
