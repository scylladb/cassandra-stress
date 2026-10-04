// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.operations;

import org.apache.cassandra.stress.Operation;

public interface OpDistribution
{
    Operation next();
}
