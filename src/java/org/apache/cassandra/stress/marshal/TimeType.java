// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

public final class TimeType extends AbstractType<Long>
{
    public static final TimeType instance = new TimeType();

    private TimeType()
    {
        super(true);
    }

    public TypeSerializer<Long> getSerializer()
    {
        return TimeSerializer.instance;
    }
}
