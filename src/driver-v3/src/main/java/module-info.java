// SPDX-License-Identifier: Apache-2.0
/** The cassandra-stress client for the Scylla Java driver 3.x. */
// The module name follows the package name, which ends in the driver major version.
@SuppressWarnings({"module", "requires-transitive-automatic"})
module org.apache.cassandra.stress.driver.v3 {
    requires transitive com.scylladb.driver.core;
    requires transitive org.apache.cassandra.stress;
    requires org.slf4j;

    exports org.apache.cassandra.stress.driver.v3;
    exports org.apache.cassandra.stress.driver.v3.codecs;

    provides org.apache.cassandra.stress.driver.StressClientProvider with
            org.apache.cassandra.stress.driver.v3.V3ClientProvider;
}
