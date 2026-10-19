## Commands

```bash
# Compile check (main, unit test and integration test sources)
./gradlew testClasses integrationTestClasses
# Test (all unit tests, as CI does)
./gradlew test
# One test class
./gradlew :<module>:test --tests <SimpleClassName>
# Coverage report of all modules (build/reports/jacoco/jacocoTestReport/html)
./gradlew jacocoTestReport
# Integration tests against ScyllaDB in Docker (Testcontainers)
./gradlew integrationTest
# One driver: native or 4x
./gradlew integrationTest -PintegrationDriver=native
# One integration test class, on the native driver or on the 4x driver
./gradlew :core:integrationTest --tests <Name>IT
./gradlew :core:integrationTest4x --tests <Name>IT
# The installed launcher against ScyllaDB in Docker, with both drivers
./gradlew distributionTest
# Install the deb and the rpm in Docker (-Dstress.package.images=, -Dstress.package.java=)
./gradlew packageTest
# One report for unit and integration tests
./gradlew jacocoFullReport
# Format all Java sources (palantir-java-format)
./gradlew format
# Format check of Java and Gradle scripts, Error Prone, Checkstyle, PMD and SpotBugs
./gradlew lint
# Every jar of the distribution is a named module
./gradlew modulePathCheck
# Archives, checksums, deb and rpm in build/distributions
./gradlew assemble
# Docker image from the installed distribution
./gradlew dockerImage
```

<!-- qatools-sdlc:begin -->
## Development flow

This repository uses the `qatools-sdlc` plugin. Every piece of work goes
through its flow: `/qatools-sdlc:intent <KEY>`, then `/qatools-sdlc:spec` or
`/qatools-sdlc:rca` for a bug, then `/qatools-sdlc:plan` (optional for a
bug), then the code.
Commit each artifact before the stage that consumes it. A review works
through `/qatools-sdlc:review`. File a Jira issue about our work with
`/qatools-sdlc:issue`. It needs the Atlassian connector.
The user may skip the flow for a very small fix when they say so. The pull
request description then states the skip in one line.

If the `/qatools-sdlc:*` skills are not available, stop and ask the user to
run these two commands, then start a new session:

    /plugin marketplace add git@github.com:scylladb/qatools.git
    /plugin install qatools-sdlc@qatools

Jira keys: `QATOOLS-<n>`. Task artifacts: `tasks/<KEY>/`, or
`tasks/<PARENT>/<KEY>/` for a subtask. Read `docs/INDEX.md` before any task
and follow the standards in `docs/standards/`. Suggest
`/qatools-sdlc:standards-update` when a convention comes up that no standard
holds. Verify sequence: section `Commands` of this file.
<!-- qatools-sdlc:end -->
