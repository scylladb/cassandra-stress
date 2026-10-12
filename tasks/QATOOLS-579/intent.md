# QATOOLS-579 — Move the build from Ant XML to task-oriented Gradle builds

## Problem

cassandra-stress builds with Apache Ant from a 729-line `build.xml`. The
build has no typed model of its tasks. Each step is XML with string
properties, and a change to one target can break another without an error
until it runs.

Several build steps live outside the build:

- `scripts/build_deb.sh` and `scripts/build_rpm.sh` build the deb and the rpm
  from `dist/debian` and `dist/redhat`.
- The shell scripts in `integration-tests/` test the launcher, and
  `scripts/install-deb.sh`, `scripts/install-rpm.sh` and
  `scripts/check-version.sh` test the packages. Only CI runs them, against a
  `compose.yml` node.
- The Docker image installs Ant and builds stress again in its own stage.
- CI downloads Ant at run time through `.github/actions/setup-ant`.

The version in `build.xml` stays at 3.12.0, and no release updates it. The
latest release tag is v3.21.1.

Stress runs on the classpath. All the code is one compile unit, and nothing
stops a package from reaching into a driver.

## Who it affects

- The developers of cassandra-stress, who build, test and lint locally.
- The CI and release pipelines in `.github/workflows`, which build the
  archives, the deb, the rpm and the Docker image.
- The users of the release artifacts and of the Docker image, who see the
  version that stress prints.
- Renovate, which reads the dependency versions from `build.xml`.

## Evidence

`build.xml` on `origin/master`:

```xml
    <property name="base.version" value="3.12.0" />
    <property name="base.javaDriverVersion" value="3.11.5.20" />
    <property name="base.java4DriverVersion" value="4.19.2.2" />
```

`git tag --sort=-v:refname | head -1`:

```
v3.21.1
```

`Dockerfile` on `origin/master`:

```
FROM eclipse-temurin:21.0.12_8-jdk-noble AS build
    && apt install -y ant
RUN --mount=type=cache,target=/root/.m2 ant realclean \
    && ant ${BUILD_OPTS} \
    && ant ${BUILD_OPTS} -Drelease=true -Drelease.version=21 -Dversion="${CASSANDRA_STRESS_VERSION}" artifacts \
```

`.github/actions/setup-ant/action.yml` on `origin/master`:

```
name: Setup Ant
description: Install the current Apache Ant release and export ANT_HOME.
```

`.github/workflows/test.yml` on `origin/master`:

```
  enumerated-tests:
    timeout-minutes: 2
    name: "Enumerate all tests from integration-tests folder to run them independently"
```

`renovate.json` on `origin/master` reads the versions from `build.xml` with
regex managers:

```
['/(^|/)build\\.xml$/'] ['<property name="base.javaDriverVersion" value="(?<currentValue>[\\w+\\.\\-]*)"']
```

## What good looks like

- One build tool with a typed build language runs every step as a task:
  compile, unit tests, integration tests, lint, format, the tar and zip
  archives, the source archive, the checksums, the deb, the rpm and the
  Docker image.
- A developer and CI run the same tasks. No build or test step lives in a
  shell script outside the build.
- The launcher and package tests run as tasks on any machine with Docker.
- The version that stress prints comes from the release tag.
- Renovate updates the dependencies, the build tool and the base images
  without regex managers.
- The release artifacts keep their names.
- Stress runs on JDK 21 and on every later LTS release.

## Out of scope

- Changes to the stress commands, options or output.
- New release channels, such as a package repository.
- Changes to the release tags or to the release workflow trigger.
