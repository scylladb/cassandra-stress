// SPDX-License-Identifier: Apache-2.0
/**
 * The cassandra-stress engine: settings, data generation, operations and
 * reports. The driver modules provide the {@link
 * org.apache.cassandra.stress.driver.StressClientProvider} service.
 */
module org.apache.cassandra.stress {
    requires transitive org.apache.cassandra.stress.marshal;
    requires transitive org.apache.cassandra.stress.util;
    requires com.fasterxml.jackson.databind;
    requires java.management;
    requires jdk.unsupported;
    requires org.apache.commons.math3;
    requires org.hdrhistogram.HdrHistogram;
    requires org.jctools.core;
    requires org.slf4j;
    requires org.yaml.snakeyaml;

    exports org.apache.cassandra.stress;
    exports org.apache.cassandra.stress.core;
    exports org.apache.cassandra.stress.driver;
    exports org.apache.cassandra.stress.generate;
    exports org.apache.cassandra.stress.generate.values;
    exports org.apache.cassandra.stress.operations;
    exports org.apache.cassandra.stress.operations.predefined;
    exports org.apache.cassandra.stress.operations.userdefined;
    exports org.apache.cassandra.stress.report;
    exports org.apache.cassandra.stress.settings;

    // SnakeYAML builds the profile beans by reflection.
    opens org.apache.cassandra.stress to
            org.yaml.snakeyaml;

    uses org.apache.cassandra.stress.driver.StressClientProvider;
}
