// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.List;

abstract class Option {

    abstract boolean accept(String param);

    abstract boolean happy();

    abstract String shortDisplay();

    abstract String longDisplay();

    abstract String getOptionAsString();

    abstract List<String> multiLineDisplay();

    abstract boolean setByUser();

    abstract boolean present();

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @SuppressWarnings("EqualsGetClass")
    @Override
    public boolean equals(Object that) {
        return that != null && this.getClass() == that.getClass();
    }
}
