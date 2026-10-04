// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import java.io.Serializable;

public interface RatioDistributionFactory extends Serializable
{

    RatioDistribution get();
    String getConfigAsString();

}
