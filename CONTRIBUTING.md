# Contributing to cassandra-stress

## Before you write code

1. Open an issue in the `QATOOLS` Jira project.
2. Read `docs/INDEX.md` and the standards in `docs/standards/`.
3. Write the task artifacts in `tasks/<KEY>/` and commit each one before the code that uses it.

## Build and check

Use JDK 21 or a later LTS release, such as JDK 25. The Gradle wrapper downloads Gradle.

| Command | Result |
|---|---|
| `./gradlew testClasses integrationTestClasses` | Compiles the main and the test code |
| `./gradlew test` | Runs the unit tests |
| `./gradlew integrationTest` | Runs the integration tests against ScyllaDB in Docker |
| `./gradlew distributionTest` | Runs `bin/cassandra-stress` of the installed distribution against ScyllaDB in Docker |
| `./gradlew packageTest` | Installs the deb and the rpm in Docker and runs `cassandra-stress version` |
| `./gradlew format` | Formats the Java code with palantir-java-format |
| `./gradlew lint` | Runs the format check, Error Prone, Checkstyle, PMD and SpotBugs |
| `./gradlew dockerImage` | Builds the Docker image from the installed distribution |
| `./gradlew installDist` | Writes a runnable tree to `build/install/cassandra-stress` |
| `./gradlew assemble` | Writes the archives, the checksums, the deb and the rpm to `build/distributions`. The version comes from the latest `v*` git tag, or from `-Pversion=<version>` |

Run `./gradlew format`, `./gradlew lint` and `./gradlew test` before each commit.

## Code style

The formatter sets the layout. `docs/standards/backend/java-conventions.md` holds the other rules, such as braces on every `if`, `for` and `while` body.

## Commits and pull requests

Write commit subjects as `type(scope): KEY subject`, as `docs/standards/global/conventions.md` states. Open the pull request against `master` and end its description with `closes KEY` or `refs KEY`.
