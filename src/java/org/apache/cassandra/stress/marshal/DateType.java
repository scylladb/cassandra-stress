// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.util.Date;

public final class DateType extends AbstractType<Date>
{
    public static final DateType instance = new DateType();

    private DateType()
    {
        super(true);
    }

    public TypeSerializer<Date> getSerializer()
    {
        return TimestampSerializer.instance;
    }
}
