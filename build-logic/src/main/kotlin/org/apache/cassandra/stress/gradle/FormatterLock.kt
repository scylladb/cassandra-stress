package org.apache.cassandra.stress.gradle

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Lets one Spotless task run at a time. With parallel Spotless tasks in two
 * projects, palantir-java-format fails with NoClassDefFoundError.
 */
abstract class FormatterLock : BuildService<BuildServiceParameters.None>
