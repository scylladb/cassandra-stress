// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

public class MarshalException extends RuntimeException
{
    public MarshalException(String message)
    {
        super(message);
    }

    public MarshalException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
