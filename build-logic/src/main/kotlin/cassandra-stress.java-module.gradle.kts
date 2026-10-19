import com.diffplug.gradle.spotless.SpotlessTask
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import org.apache.cassandra.stress.gradle.ErrorProneCheck
import org.apache.cassandra.stress.gradle.FormatterLock
import org.apache.cassandra.stress.gradle.useModuleJars
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.time.Duration

plugins {
    `java-library`
    jacoco
    checkstyle
    pmd
    id("com.github.spotbugs")
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun version(name: String): String = libs.findVersion(name).get().requiredVersion

fun library(name: String) = libs.findLibrary(name).get()

val javaRelease = 21
val lintConfig = layout.settingsDirectory.dir("lint")

useModuleJars()

val errorProne = configurations.dependencyScope("errorProne")
val errorProneClasspath = configurations.resolvable("errorProneClasspath") {
    extendsFrom(errorProne.get())
}

dependencies {
    testImplementation(platform(library("junit-bom")))
    testImplementation(library("junit-jupiter"))
    testRuntimeOnly(library("junit-platform-launcher"))
    "errorProne"(library("errorprone-core"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaRelease
    options.encoding = "UTF-8"
    options.compilerArgs.add("-proc:none")
    options.javaModuleVersion = provider { project.version.toString() }
}

tasks.jar {
    archiveFileName = "cassandra-stress-${project.name}.jar"
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

// Extra JVM arguments for every test JVM, for example
// -PtestJvmArgs=--sun-misc-unsafe-memory-access=deny on JDK 25.
val testJvmArgs = providers.gradleProperty("testJvmArgs")
    .map { args -> args.split(" ").filter { it.isNotBlank() } }
    .orElse(emptyList())

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // The tests read the example profiles and the configuration relative to the repository root.
    workingDir = layout.settingsDirectory.asFile
    listOf("examples", "conf").forEach { dir ->
        inputs.dir(layout.settingsDirectory.dir(dir)).withPropertyName(dir).withPathSensitivity(PathSensitivity.RELATIVE)
    }
    enableAssertions = true
    maxHeapSize = "1024m"
    systemProperty("com.datastax.oss.driver.shaded.netty.noUnsafe", "true")
    systemProperty("com.datastax.shaded.netty.noUnsafe", "true")
    val extraJvmArgs = testJvmArgs
    jvmArgumentProviders.add(CommandLineArgumentProvider { extraJvmArgs.get() })
    testLogging {
        events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
    }
    extensions.configure<JacocoTaskExtension> {
        includes = listOf("org.apache.cassandra.stress.*")
    }
}

tasks.test {
    filter {
        includeTestsMatching("*Test")
    }
    timeout = Duration.ofMinutes(10)
}

jacoco {
    toolVersion = version("jacoco")
}

// One Error Prone task for each source set, such as errorProneMain and errorProneTest.
val errorProneCompiler = javaToolchains.compilerFor(java.toolchain)
sourceSets.configureEach {
    val sourceSet = this
    val task = tasks.register<ErrorProneCheck>(getTaskName("errorProne", null)) {
        description = "Checks the ${sourceSet.name} sources with Error Prone."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        compiler = errorProneCompiler
        sources.from(sourceSet.java)
        classpath.from(sourceSet.compileClasspath)
        errorProneClasspath.from(configurations.named("errorProneClasspath"))
        release = javaRelease
        disabledChecks = listOf(
            "JavaUtilDate",
            "StringSplitter",
            "ImmutableEnumChecker",
            "AnnotateFormatMethod",
            "ExposedPrivateType",
            "EffectivelyPrivate",
        )
        classesDirectory = layout.buildDirectory.dir("errorprone/${sourceSet.name}")
        report = layout.buildDirectory.file("reports/errorprone/${sourceSet.name}.txt")
    }
    tasks.check {
        dependsOn(task)
    }
}

val formatterLock = gradle.sharedServices.registerIfAbsent("formatterLock", FormatterLock::class) {
    maxParallelUsages = 1
}

tasks.withType<SpotlessTask>().configureEach {
    usesService(formatterLock)
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(version("palantir-java-format")).style("PALANTIR")
    }
}

checkstyle {
    toolVersion = version("checkstyle")
    configFile = lintConfig.file("checkstyle.xml").asFile
    maxWarnings = 0
}

pmd {
    toolVersion = version("pmd")
    ruleSetFiles = files(lintConfig.file("pmd.xml"))
    ruleSets = emptyList()
    rulesMinimumPriority = 5
    isConsoleOutput = true
}

spotbugs {
    toolVersion = version("spotbugs")
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    excludeFilter = lintConfig.file("spotbugs-exclude.xml")
    maxHeapSize = "1024m"
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("text") { required = true }
    reports.create("html") { required = true }
}

tasks.register("lint") {
    description = "Runs the format check, Error Prone, Checkstyle, PMD and SpotBugs."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.spotlessCheck, tasks.withType<ErrorProneCheck>(), tasks.withType<Checkstyle>(), tasks.withType<Pmd>(), tasks.withType<SpotBugsTask>())
}

tasks.register("format") {
    description = "Formats the Java sources with palantir-java-format."
    group = "formatting"
    dependsOn(tasks.spotlessApply)
}
