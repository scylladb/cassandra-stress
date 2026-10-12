// SPDX-License-Identifier: Apache-2.0
/** The cassandra-stress client for the Scylla Java driver 4.x. */
// The module name follows the package name, which ends in the driver major version.
@SuppressWarnings({"module", "requires-transitive-automatic"})
module org.apache.cassandra.stress.driver.v4 {
    requires transitive com.datastax.oss.driver.core;
    requires transitive org.apache.cassandra.stress;
    requires org.slf4j;

    exports org.apache.cassandra.stress.driver.v4;
    exports org.apache.cassandra.stress.driver.v4.codecs;

    provides org.apache.cassandra.stress.driver.StressClientProvider with
            org.apache.cassandra.stress.driver.v4.V4ClientProvider;
}
