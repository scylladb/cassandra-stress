import java.time.Duration

plugins {
    id("cassandra-stress.java-module")
}

description = "The cassandra-stress engine: settings, data generation, operations and reports."

dependencies {
    api(project(":util"))
    api(project(":marshal"))
    implementation(libs.commons.math3)
    implementation(libs.snakeyaml)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jctools)
    implementation(libs.hdrhistogram)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation(project(":driver-v3"))
    testImplementation(project(":driver-v4"))
    testRuntimeOnly(libs.reactive.streams)
}

val generateVersionResource = tasks.register("generateVersionResource") {
    description = "Writes the stress.version resource that the version command prints."
    val version = project.version.toString()
    val outputDir = layout.buildDirectory.dir("generated/resources/version")
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("org/apache/cassandra/stress/stress.version").asFile
        file.parentFile.mkdirs()
        file.writeText(version)
    }
}

sourceSets.main {
    resources.srcDir(generateVersionResource)
}

// Pass -Dscylla.image=... or -Dstress.previous.image=... to the integration tests.
val integrationTestProperties = providers.systemPropertiesPrefixedBy("scylla.")
    .zip(providers.systemPropertiesPrefixedBy("stress.")) { scylla, stress -> scylla + stress }

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(project())
                implementation(libs.testcontainers.scylladb) {
                    exclude(group = "junit", module = "junit")
                }
                implementation(project(":driver-v4"))
                implementation(libs.hdrhistogram)
                runtimeOnly(project(":driver-v3"))
                runtimeOnly(libs.reactive.streams)
            }
        }
    }
}

val integrationTest = sourceSets["integrationTest"]

val integrationDrivers = listOf("native", "4x")

// -PintegrationDriver=native or -PintegrationDriver=4x runs the integration tests with one driver only.
val integrationDriver = providers.gradleProperty("integrationDriver").orNull?.also {
    if (it !in integrationDrivers) {
        throw GradleException("-PintegrationDriver=$it is not a driver. Use one of ${integrationDrivers.joinToString()}.")
    }
}

// The integration tests run once for each driver.
val integrationTestTasks = integrationDrivers.map { driver ->
    val name = if (driver == "native") "integrationTest" else "integrationTest4x"
    val task = if (driver == "native") tasks.named<Test>(name) else tasks.register<Test>(name)
    task.configure {
        description = "Runs the integration tests against ScyllaDB in Docker with the $driver driver."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = integrationTest.output.classesDirs
        classpath = integrationTest.runtimeClasspath
        useJUnitPlatform {
            excludeTags("distribution", "package")
        }
        filter {
            includeTestsMatching("*IT")
        }
        timeout = Duration.ofMinutes(30)
        systemProperties(integrationTestProperties.get())
        systemProperty("stress.it.driver", driver)
        // Skips the fixed sleeps of stress, and lets the 4x driver see a schema change after 10 ms.
        systemProperty("stress.skipSleeps", "true")
        systemProperty("datastax-java-driver.advanced.metadata.schema.debouncer.window", "10ms")
        val selected = integrationDriver ?: driver
        onlyIf("-PintegrationDriver selects the $driver driver") { selected == driver }
        shouldRunAfter(tasks.test)
    }
    task
}

tasks.named("integrationTest") {
    finalizedBy(integrationTestTasks.drop(1))
}

// The JaCoCo data of both integration test tasks, for the full coverage report of the root project.
configurations.consumable("integrationCoverageElements") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
        attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.JACOCO_RESULTS))
        attribute(TestSuiteName.TEST_SUITE_NAME_ATTRIBUTE, objects.named("integration"))
    }
    integrationTestTasks.forEach { task ->
        outgoing.artifact(task.map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! }) {
            type = "binary"
            builtBy(task)
        }
    }
}

val rootBuild = rootProject.layout.buildDirectory
val launcherJvmOpts = providers.gradleProperty("testJvmArgs")

// Runs the launcher of the installed distribution, or installs the deb and the rpm in Docker.
fun registerPackagingTest(name: String, tag: String, describe: String, configure: Test.() -> Unit) = tasks.register<Test>(name) {
    description = describe
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform {
        includeTags(tag)
    }
    timeout = Duration.ofMinutes(60)
    systemProperties(integrationTestProperties.get())
    systemProperty("stress.version", project.version.toString())
    shouldRunAfter(integrationTestTasks)
    configure()
}

registerPackagingTest(
    "distributionTest",
    "distribution",
    "Runs bin/cassandra-stress of the installed distribution against ScyllaDB in Docker with both drivers.",
) {
    val installDir = rootBuild.dir("install/cassandra-stress")
    dependsOn(":installDist")
    inputs.files(installDir).withPropertyName("distribution").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("stress.distribution", installDir.get().asFile.absolutePath)
    // The launcher uses the JDK of the test JVM, so -PtestJvmArgs also reaches it.
    val jvmOpts = launcherJvmOpts
    doFirst {
        val test = this as Test
        test.environment("JAVA_HOME", test.javaLauncher.get().metadata.installationPath.asFile.absolutePath)
        jvmOpts.orNull?.let { test.environment("JVM_OPTS", it) }
    }
}

registerPackagingTest(
    "packageTest",
    "package",
    "Installs the deb and the rpm with a Temurin runtime in Docker and runs cassandra-stress version.",
) {
    val distributions = rootBuild.dir("distributions")
    dependsOn(":buildDeb", ":buildRpm")
    inputs.files(distributions.map { it.asFileTree.matching { include("*.deb", "*.rpm") } })
        .withPropertyName("packages")
        .withPathSensitivity(PathSensitivity.NAME_ONLY)
    systemProperty("stress.package.dir", distributions.get().asFile.absolutePath)
    // The test installs a Java runtime from the network, so a cached result can hide a broken package repository.
    outputs.doNotCacheIf("the test installs packages from the network") { true }
}
