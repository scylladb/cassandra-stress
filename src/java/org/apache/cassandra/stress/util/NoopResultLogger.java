// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

public class NoopResultLogger implements ResultLogger {
    NoopResultLogger() {}

    @Override
    public void println(String line) {}

    @Override
    public void println() {}

    @Override
    public void printException(Exception e) {}

    @Override
    public void flush() {}

    @Override
    public void printf(String s, Object... args) {}
}
