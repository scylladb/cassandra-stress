pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
    // -PscyllaDriver3Version=<version> and -PscyllaDriver4Version=<version> build
    // with another release of a driver.
    versionCatalogs {
        create("libs") {
            providers.gradleProperty("scyllaDriver3Version").orNull?.let { version("scylla-driver3", it) }
            providers.gradleProperty("scyllaDriver4Version").orNull?.let { version("scylla-driver4", it) }
        }
    }
}

rootProject.name = "cassandra-stress"

// Each Java module is a subproject in src/<name>.
listOf("util", "marshal", "core", "driver-v3", "driver-v4").forEach { name ->
    include(name)
    project(":$name").projectDir = file("src/$name")
}

// The version is -Pversion when it is set. Otherwise it comes from the latest
// v<major>.<minor>.<patch> tag, and a pre-release tag such as v3.22.0-rc1 does
// not count. A clean checkout of the tag builds the tag version, and any other
// tree builds the next patch as a snapshot. The source archive has no git
// history, so it carries the version in a VERSION file.
val tagPattern = Regex("""v(\d+)\.(\d+)\.(\d+)(-\d+-g[0-9a-f]+)?(-dirty)?""")

fun versionFromGit(): String? {
    val described = runCatching {
        providers.exec {
            commandLine("git", "describe", "--tags", "--match", "v[0-9]*.[0-9]*.[0-9]*", "--exclude", "v*-*", "--dirty")
            workingDir = settingsDir
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    }.getOrNull() ?: return null
    val match = tagPattern.matchEntire(described) ?: return null
    val (major, minor, patch, commits, dirty) = match.destructured
    return if (commits.isEmpty() && dirty.isEmpty()) {
        "$major.$minor.$patch"
    } else {
        "$major.$minor.${patch.toInt() + 1}-SNAPSHOT"
    }
}

val stressVersion = providers.gradleProperty("version").orNull
    ?: versionFromGit()
    ?: providers.fileContents(layout.rootDirectory.file("VERSION")).asText.orNull?.trim()
    ?: "0.0.0-SNAPSHOT"

gradle.allprojects {
    version = stressVersion
}

