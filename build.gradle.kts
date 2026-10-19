import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.CopySpecEnhancement
import com.netflix.gradle.plugins.packaging.SystemPackagingTask
import com.netflix.gradle.plugins.rpm.Rpm
import org.apache.cassandra.stress.gradle.Checksums
import org.apache.cassandra.stress.gradle.ModulePathCheck
import org.apache.cassandra.stress.gradle.useModuleJars
import org.redline_rpm.header.Architecture
import org.redline_rpm.header.Flags
import org.redline_rpm.header.Os
import org.redline_rpm.payload.Directive

plugins {
    base
    `jacoco-report-aggregation`
    id("cassandra-stress.packaging")
    id("cassandra-stress.kotlin-format")
}

description = "Cassandra-stress is a Java-based command-line stress testing tool for Apache Cassandra and ScyllaDB."

val installPrefix = "/usr/share/cassandra-stress"
val stressModules = listOf(":util", ":marshal", ":core", ":driver-v3", ":driver-v4")

useModuleJars()

// The modules and their runtime dependencies, the content of lib/ in every distribution.
val distributionModules = configurations.dependencyScope("distributionModules")
val distributionRuntime = configurations.resolvable("distributionRuntime") {
    extendsFrom(distributionModules.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
    }
}

// The JaCoCo data that :core publishes for its integration test tasks.
val integrationCoverage = configurations.dependencyScope("integrationCoverage")
val integrationCoverageData = configurations.resolvable("integrationCoverageData") {
    extendsFrom(integrationCoverage.get())
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
        attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.JACOCO_RESULTS))
        attribute(TestSuiteName.TEST_SUITE_NAME_ATTRIBUTE, objects.named("integration"))
    }
}

dependencies {
    "integrationCoverage"(project(":core"))
    stressModules.forEach {
        "distributionModules"(project(it))
        jacocoAggregation(project(it))
    }
}

reporting {
    reports {
        register<JacocoCoverageReport>("jacocoTestReport") {
            testSuiteName = "test"
        }
    }
}

// One report for the unit tests of every module and the integration tests of both drivers.
tasks.register<JacocoReport>("jacocoFullReport") {
    description = "Writes one coverage report for the unit and the integration tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    val unitReport = tasks.named<JacocoReport>("jacocoTestReport")
    dependsOn(unitReport)
    executionData.from(unitReport.map { it.executionData })
    executionData.from(integrationCoverageData)
    sourceDirectories.from(unitReport.map { it.sourceDirectories })
    classDirectories.from(unitReport.map { it.classDirectories })
    reports {
        xml.required = true
        csv.required = true
        html.required = true
    }
}

val modulePathCheck = tasks.register<ModulePathCheck>("modulePathCheck") {
    description = "Checks that every jar in lib/ of the distribution is a named module."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    modulePath.from(distributionRuntime)
    report = layout.buildDirectory.file("reports/modulePathCheck/unnamed.txt")
}

tasks.check {
    dependsOn(modulePathCheck)
}

tasks.register("integrationTest") {
    description = "Runs the integration tests against ScyllaDB in Docker with both drivers."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(":core:integrationTest", ":core:integrationTest4x")
}

distributions {
    main {
        distributionBaseName = "cassandra-stress"
        contents {
            into("lib") {
                from(distributionRuntime)
            }
            into("bin") {
                from("bin")
                filePermissions { unix("rwxr-xr-x") }
            }
            into("conf") {
                from("conf")
            }
            into("examples") {
                from("examples") { include("*.yaml") }
            }
            from(layout.projectDirectory) { include("*.txt") }
        }
    }
}

// The archive name sets the top directory, so the archives unpack to cassandra-stress/.
tasks.withType<AbstractArchiveTask>().named { it == "distTar" || it == "distZip" }.configureEach {
    archiveVersion = ""
}

tasks.distTar {
    compression = Compression.GZIP
    archiveExtension = "tar.gz"
}

// The source archive holds the files that git tracks at HEAD, without the
// development notes and the Docker build files, and a VERSION file with the
// version of the build. A tree without git history, such as an unpacked source
// archive, builds every other artifact.
val isGitCheckout = layout.projectDirectory.file(".git").asFile.exists()

val trackedSources = tasks.register<Exec>("trackedSources") {
    description = "Writes the files that git tracks at HEAD to a tar file."
    val archive = layout.buildDirectory.file("tmp/sourceTar/tracked.tar")
    val gitCheckout = isGitCheckout
    onlyIf("the project directory is a git checkout") { gitCheckout }
    outputs.file(archive)
    outputs.upToDateWhen { false }
    workingDir = layout.projectDirectory.asFile
    commandLine(
        "git", "-c", "tar.umask=0022", "archive", "--format=tar", "--output=${archive.get().asFile.absolutePath}", "HEAD", "--", ".",
        ":(exclude).claude", ":(exclude)CLAUDE.md", ":(exclude)tasks", ":(exclude)docs/standards",
        ":(exclude)docs/INDEX.md", ":(exclude)Dockerfile", ":(exclude).dockerignore",
    )
    doFirst { archive.get().asFile.parentFile.mkdirs() }
}

val sourceVersionFile = tasks.register("sourceVersionFile") {
    description = "Writes the VERSION file of the source archive."
    val stressVersion = project.version.toString()
    val output = layout.buildDirectory.file("tmp/sourceTar/VERSION")
    inputs.property("version", stressVersion)
    outputs.file(output)
    doLast { output.get().asFile.writeText(stressVersion + "\n") }
}

val sourceTar = tasks.register<Tar>("sourceTar") {
    description = "Assembles the source archive from the files that git tracks at HEAD."
    group = "distribution"
    val gitCheckout = isGitCheckout
    onlyIf("the project directory is a git checkout") { gitCheckout }
    compression = Compression.GZIP
    archiveFileName = "cassandra-stress-src.tar.gz"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    // Keeps the modes that git records, such as the executable bit of gradlew.
    useFileSystemPermissions()
    into("cassandra-stress-src") {
        from(tarTree(trackedSources.map { it.outputs.files.singleFile }))
        from(sourceVersionFile)
    }
}

ospackage {
    packageName = "cassandra-stress"
    summary = "Stress testing tool for Apache Cassandra and ScyllaDB"
    packageDescription = project.description
    maintainer = "Dusan Malusev <dusan.malusev@scylladb.com>"
    vendor = "ScyllaDB"
    packager = "ScyllaDB"
    url = "https://github.com/scylladb/cassandra-stress"
    license = "Apache-2.0"
    user = "root"
    permissionGroup = "root"
}

// The package keeps the files in $installPrefix and links the command and the
// configuration into it, so the launcher sees one layout for every install.
tasks.withType<SystemPackagingTask>().configureEach {
    notCompatibleWithConfigurationCache("The nebula ospackage tasks read the project at execution time.")
    // Redline writes into an existing package file without truncating it, so
    // a smaller package keeps the stale tail of the earlier one.
    doFirst { (this as SystemPackagingTask).determineArchiveFile().get().asFile.delete() }
    from(distributionRuntime) { into("$installPrefix/lib") }
    from("examples") {
        include("*.yaml")
        into("$installPrefix/examples")
    }
    from("LICENSE.txt") {
        into(installPrefix)
        CopySpecEnhancement.fileType(this, Directive(Directive.RPMFILE_LICENSE))
    }
    from("NOTICE.txt") {
        into(installPrefix)
        CopySpecEnhancement.fileType(this, Directive(Directive.RPMFILE_DOC))
    }
    from("bin/cassandra-stress") {
        into("$installPrefix/bin")
        filePermissions { unix("rwxr-xr-x") }
    }
    from("conf") {
        into("/etc/cassandra-stress")
        CopySpecEnhancement.fileType(this, Directive(Directive.RPMFILE_CONFIG or Directive.RPMFILE_NOREPLACE))
    }
    link("$installPrefix/conf", "/etc/cassandra-stress")
    link("/usr/bin/cassandra-stress", "$installPrefix/bin/cassandra-stress")
}

tasks.withType<Deb>().configureEach {
    setArch("all")
    from("packaging/debian/copyright") { into("/usr/share/doc/cassandra-stress") }
    packageGroup = "utils"
    priority = "optional"
    requires("temurin-21-jre")
        .or("temurin-25-jre", "", 0)
        .or("openjdk-21-jre-headless", "", 0)
        .or("openjdk-25-jre-headless", "", 0)
        .or("java21-runtime-headless", "", 0)
    requires("awk")
    fileTree("conf").forEach { configurationFile("/etc/cassandra-stress/${it.name}") }
}

tasks.withType<Rpm>().configureEach {
    setArch(Architecture.NOARCH)
    os = Os.LINUX
    release = "1"
    requires("rpmlib(RichDependencies)", "4.12.0-1", Flags.LESS or Flags.EQUAL or Flags.RPMLIB)
    requires(
        "(temurin-21-jre or temurin-25-jre or temurin-21-jdk or temurin-25-jdk " +
            "or jre-21-headless or jre-25-headless)",
    )
    listOf("bash", "coreutils", "findutils", "gawk", "grep").forEach { requires(it) }
}

// The Docker image holds the tree that installDist writes. -PdockerImage sets the
// repository, -PdockerPlatforms the platforms, and -PdockerPush=true pushes the
// image instead of loading it into the local Docker.
val dockerRepository = providers.gradleProperty("dockerImage").orElse("scylladb/cassandra-stress")
val dockerPlatforms = providers.gradleProperty("dockerPlatforms")
val dockerPush = providers.gradleProperty("dockerPush").map { it.toBoolean() }.orElse(false)

// A Gradle daemon looks up a bare command on the PATH it started with, so the
// task finds docker on the PATH of the current build.
val dockerExecutable = providers.environmentVariable("PATH")
    .map { path ->
        path.split(File.pathSeparator).map { File(it, "docker") }.firstOrNull { it.canExecute() }?.absolutePath
            ?: "docker"
    }
    .orElse("docker")

tasks.register<Exec>("dockerImage") {
    description = "Builds the Docker image from the installed distribution with docker buildx."
    group = "distribution"
    dependsOn(tasks.installDist)
    val stressVersion = project.version.toString()
    val repository = dockerRepository.get()
    workingDir = layout.projectDirectory.asFile
    commandLine(
        buildList {
            addAll(listOf(dockerExecutable.get(), "buildx", "build", "--pull"))
            addAll(listOf("--build-arg", "CASSANDRA_STRESS_VERSION=$stressVersion"))
            addAll(listOf("--tag", "$repository:$stressVersion", "--tag", "$repository:latest"))
            dockerPlatforms.orNull?.let { addAll(listOf("--platform", it)) }
            add(if (dockerPush.get()) "--push" else "--load")
            add(".")
        },
    )
}

val checksums = tasks.register<Checksums>("checksums") {
    description = "Writes the SHA-256 and SHA-512 checksums of the binary and the source archives."
    group = "distribution"
    archives.from(tasks.distTar, tasks.distZip, sourceTar)
}

tasks.assemble {
    dependsOn(tasks.distTar, tasks.distZip, sourceTar, checksums, tasks.buildDeb, tasks.buildRpm)
}
