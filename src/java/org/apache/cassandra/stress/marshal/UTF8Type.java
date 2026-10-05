// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

public final class UTF8Type extends AbstractType<String> {
    public static final UTF8Type instance = new UTF8Type();

    private UTF8Type() {
        super(true);
    }

    @Override
    public TypeSerializer<String> getSerializer() {
        return UTF8Serializer.instance;
    }
}
