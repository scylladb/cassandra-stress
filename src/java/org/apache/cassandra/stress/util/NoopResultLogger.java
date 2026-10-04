// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

public class NoopResultLogger implements ResultLogger
{
    NoopResultLogger() { }

    public void println(String line)
    {
    }

    public void println()
    {
    }

    public void printException(Exception e)
    {
    }

    public void flush()
    {
    }

    public void printf(String s, Object... args)
    {
    }
}
