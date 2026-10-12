// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface StressBoundStatement {
    StressPreparedStatement statement();
}
