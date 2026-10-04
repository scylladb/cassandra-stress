// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import java.io.Serializable;

public interface DistributionFactory extends Serializable
{

    Distribution get();
    String getConfigAsString();

}
