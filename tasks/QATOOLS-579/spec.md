# QATOOLS-579 — Move the build from Ant XML to task-oriented Gradle builds

## Overview

Gradle 9 with the Kotlin DSL replaces Ant. The code splits into five Gradle
subprojects under `src/`, and each subproject is a Java module. Convention
plugins in `build-logic` hold the compile, test and lint setup that every
module shares. The root project builds the archives, the deb, the rpm and the
Docker image from one `installDist` tree. The launcher runs stress on the
module path. The launcher and package tests are JUnit tests that Gradle runs
against Testcontainers. The version comes from the latest release tag.

## Constraints

- The code compiles with `--release 21` and runs on JDK 21 and on every
  later LTS release. CI runs JDK 21 and 25.
- The release artifacts keep their names, so the release workflow and its
  users see no change.
- No `--add-opens` and no `--add-exports` in the launcher. A module cannot
  open a JDK package.
- The deb and the rpm keep the user edits of `/etc/cassandra-stress` across
  an upgrade from the Ant-built packages.
- Every external action, plugin, library, base image and the Gradle
  distribution have a pinned version.

## Design

### Projects and modules

| Gradle project | Directory | Java module |
|---|---|---|
| `:util` | `src/util` | `org.apache.cassandra.stress.util` |
| `:marshal` | `src/marshal` | `org.apache.cassandra.stress.marshal` |
| `:core` | `src/core` | `org.apache.cassandra.stress` |
| `:driver-v3` | `src/driver-v3` | `org.apache.cassandra.stress.driver.v3` |
| `:driver-v4` | `src/driver-v4` | `org.apache.cassandra.stress.driver.v4` |

```mermaid
flowchart LR
    marshal --> util
    core --> marshal
    core --> util
    v3[driver-v3] --> core
    v4[driver-v4] --> core
    core -. ServiceLoader .-> v3
    core -. ServiceLoader .-> v4
```

`core` finds a driver only through the `StressClientProvider` service. A
driver module provides the service with `provides` in `module-info.java` and
with a `META-INF/services` file, so the service also works on the classpath
of the tests.

A dependency jar without a module name gets an `Automatic-Module-Name` from
an artifact transform at resolution. The same transform removes the
`BlockHoundIntegration` service file of the shaded Scylla driver 3.x jar,
because it names a class that the jar does not hold.

### Build logic

| Component | Role |
|---|---|
| `gradle/libs.versions.toml` | Every library and plugin version |
| `cassandra-stress.java-module` | Compile with `--release 21`, the module version, JUnit, JaCoCo, Spotless, Error Prone, Checkstyle, PMD, SpotBugs |
| `cassandra-stress.packaging` | The `distribution` and the nebula `ospackage` plugins |
| Root `build.gradle.kts` | The distribution, the archives, the checksums, the deb, the rpm, the Docker image, the coverage report |
| `settings.gradle.kts` | The projects, the driver version overrides, the version |

Error Prone runs as its own javac process. A finding fails the `lint` task.
The compile tasks keep the plain javac output on every JDK.

### Version

```mermaid
flowchart TD
    P{-Pversion set?} -->|yes| V1[that version]
    P -->|no| G{git describe finds v*.*.*?}
    G -->|tag, clean tree| V2[the tag version]
    G -->|commits after the tag or local edits| V3[next patch with -SNAPSHOT]
    G -->|no git history| F{VERSION file?}
    F -->|yes| V4[the file content]
    F -->|no| V5[0.0.0-SNAPSHOT]
```

The source archive carries the `VERSION` file, so a build from it prints the
version of the release.

### Artifacts

```mermaid
flowchart LR
    M[five module jars + runtime jars] --> I[installDist tree]
    I --> T[tar.gz and zip]
    I --> D[Docker image]
    M --> P[deb and rpm]
    G[git archive HEAD] --> S[source tar.gz]
    T --> C[sha256 and sha512]
    S --> C
```

The deb and the rpm install into `/usr/share/cassandra-stress`, keep `conf`
in `/etc/cassandra-stress` as configuration files, and link
`/usr/bin/cassandra-stress`. The Docker image is one runtime stage that
copies the `installDist` tree.

### Tests

| Task | What runs | Where |
|---|---|---|
| `test` | The unit tests of each module | Classpath |
| `:core:integrationTest`, `:core:integrationTest4x` | Stress in-process against ScyllaDB, one task per driver | Testcontainers |
| `:core:distributionTest` | `bin/cassandra-stress` of the `installDist` tree with both drivers | Testcontainers |
| `:core:packageTest` | The deb or the rpm with a Temurin runtime in a clean image, then `cassandra-stress version` | Testcontainers |
| `jacocoFullReport` | One coverage report for the unit and the integration tests | — |

`-PintegrationDriver=native` or `-PintegrationDriver=4x` runs the integration
tests with one driver, and any other value fails the build. The
`testcontainers.yml` workflow runs `jacocoFullReport` on JDK 21 in one job for
each driver. It runs on a pull request with the `testcontainers` label and on
each push to `master`.

The ScyllaDB test node listens on its shard-aware port with the same number
on the host and in the container, because driver 3.x connects to the port
that the node advertises.

### Failure behavior

| Condition | Behavior |
|---|---|
| Java older than 21 | The launcher stops before the JVM starts and names the Java version |
| The storage directory is not writable | The launcher warns and keeps the logs in `$TMPDIR/cassandra-stress-<uid>` |
| No git checkout | `sourceTar` is skipped, every other artifact builds |
| The free shard-aware port is taken before Docker binds it | `ScyllaNode` starts the node again with a new port, up to three times |
| An Error Prone finding | `lint` fails and writes the report |

## Contracts

### Inputs

```
git describe --tags --match 'v[0-9]*.[0-9]*.[0-9]*' --dirty   # the version
git archive HEAD                                              # the source archive
```

```
-Pversion=<version>                 # the version of one build
-PscyllaDriver3Version=<version>    # another Scylla Java driver 3.x
-PscyllaDriver4Version=<version>    # another Scylla Java driver 4.x
-PtestJvmArgs=<args>                # extra JVM arguments of every test JVM and of the launcher in distributionTest
-PintegrationDriver=native|4x       # the one driver of the integration tests
-PdockerImage=<repository>          # default scylladb/cassandra-stress
-PdockerPlatforms=<platforms>       # for example linux/amd64,linux/arm64
-PdockerPush=true                   # push the image instead of loading it
-Dscylla.image=<image>              # the ScyllaDB image of the integration tests
-Dstress.package.images=<images>    # the images of packageTest
-Dstress.package.java=<versions>    # the Java versions of packageTest
```

### Outputs

`./gradlew assemble -Pversion=3.22.0` writes `build/distributions`:

```
cassandra-stress.tar.gz          cassandra-stress.tar.gz.sha256   cassandra-stress.tar.gz.sha512
cassandra-stress.zip             cassandra-stress.zip.sha256      cassandra-stress.zip.sha512
cassandra-stress-src.tar.gz      cassandra-stress-src.tar.gz.sha256  cassandra-stress-src.tar.gz.sha512
cassandra-stress_3.22.0_all.deb
cassandra-stress-3.22.0-1.noarch.rpm
```

Each checksum file holds the hex digest only. The build workflow renames the
files to the release names, such as `cassandra-stress-bin-java21.tar.gz` and
`cassandra-stress-java21_3.22.0_all.deb`.

The deb control fields:

```
Package: cassandra-stress
Architecture: all
Depends: temurin-21-jre | temurin-25-jre | openjdk-21-jre-headless | openjdk-25-jre-headless | java21-runtime-headless, awk
```

The rpm requirements:

```
Requires: (temurin-21-jre or temurin-25-jre or temurin-21-jdk or temurin-25-jdk or jre-21-headless or jre-25-headless)
Requires: bash coreutils findutils gawk grep
```

The launcher command:

```
java $JVM_OPTS --module-path lib --add-modules ALL-MODULE-PATH \
  -Dcassandra.storagedir=... -Dcassandra.logdir=... \
  -Dlogback.configurationFile=conf/logback.xml \
  -m org.apache.cassandra.stress/org.apache.cassandra.stress.Stress <args>
```

`./gradlew dockerImage` tags `<repository>:<version>` and `<repository>:latest`
with the label `org.opencontainers.image.version=<version>`.

### Module API

```java
public interface StressClientProvider {
    ConnectionAPI api();
    String name();
    String version();
    StressClient create(StressSettings settings, List<String> hosts, int port, EncryptionOptions encryptionOptions);
}
```

```java
module org.apache.cassandra.stress.driver.v3 {
    requires transitive com.scylladb.driver.core;
    requires transitive org.apache.cassandra.stress;
    provides org.apache.cassandra.stress.driver.StressClientProvider with
            org.apache.cassandra.stress.driver.v3.V3ClientProvider;
}
```

## Risks

| Risk | Response |
|---|---|
| The nebula ospackage plugin and its redline library are old and not configuration-cache safe | The package tasks opt out of the configuration cache, delete the old package before they write, and `packageTest` installs both packages in CI |
| Jars of two releases in one `lib/` stop the JVM on the module path | The README says to unpack a new release into an empty directory. The packages replace their files on upgrade |
| `--release 21` does not stop a later JDK from warning about `sun.misc.Signal` | Error Prone runs in its own javac process and fails on its findings only |
| A local `dockerImage` tags `latest` over an image pulled from Docker Hub | The README names the repository property |

## Deferred work

- A package repository for the deb and the rpm. The package names and the
  file layout stay stable for it.

## Decisions

- Gradle with the Kotlin DSL replaces Ant, and Gradle runs every build and
  test step. (spec)
- Each Gradle subproject is also a Java module, on the same branch as the
  Gradle move. (spec)
- The code compiles with `--release 21` and runs on every LTS release from
  21. (spec)
- No `--add-opens` in the launcher. The opens that the classpath build
  carried are not needed on the module path. (build)
- The test node maps the shard-aware port, so driver 3.x runs unchanged
  against a container. (build)
- The subprojects live under `src/`, and the Gradle project paths stay the
  module names. (build)
- The Makefile goes, because Gradle holds every shortcut it held. (build)
- The version comes from the release tag, so no release edits a build
  file. (build)
- Gradle builds the Docker image from the `installDist` tree. (build)
- The launcher and package scripts become JUnit tests that Gradle runs. (build)
- The integration tests run on JDK 21 only, in one job for each driver, behind
  the `testcontainers` label, so a pull request without the label does not
  wait for ScyllaDB. (build)
