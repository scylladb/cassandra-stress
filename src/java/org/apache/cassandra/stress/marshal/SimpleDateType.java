// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

public final class SimpleDateType extends AbstractType<Integer>
{
    public static final SimpleDateType instance = new SimpleDateType();

    private SimpleDateType()
    {
        super(true);
    }

    public TypeSerializer<Integer> getSerializer()
    {
        return SimpleDateSerializer.instance;
    }
}
