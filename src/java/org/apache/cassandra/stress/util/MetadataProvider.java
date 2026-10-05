// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;

@FunctionalInterface
public interface MetadataProvider {
    TableMetadata getTableMetadata(String keyspace, String tableName);
}
