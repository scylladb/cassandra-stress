// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

public interface ResultLogger
{
    static final ResultLogger NOOP = new NoopResultLogger();

    void println(String line);
    void println();
    void printException(Exception e);
    void flush();
    void printf(String s, Object... args);
}
