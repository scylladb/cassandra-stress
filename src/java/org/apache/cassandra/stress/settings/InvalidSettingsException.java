// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.io.Serial;

public final class InvalidSettingsException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Runnable helpPrinter;

    public InvalidSettingsException(String message, Runnable helpPrinter) {
        this(message, helpPrinter, null);
    }

    public InvalidSettingsException(String message, Runnable helpPrinter, Throwable cause) {
        super(message, cause);
        this.helpPrinter = helpPrinter;
    }

    public void printHelp() {
        if (helpPrinter != null) helpPrinter.run();
    }
}
