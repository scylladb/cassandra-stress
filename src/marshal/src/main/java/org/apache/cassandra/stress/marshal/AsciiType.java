// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

public final class AsciiType extends AbstractType<String> {
    public static final AsciiType instance = new AsciiType();

    private AsciiType() {
        super(true);
    }

    @Override
    public TypeSerializer<String> getSerializer() {
        return AsciiSerializer.instance;
    }
}
