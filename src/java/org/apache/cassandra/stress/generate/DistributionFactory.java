// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;


public interface DistributionFactory
{

    Distribution get();
    String getConfigAsString();

}
