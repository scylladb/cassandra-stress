# Documentation Index

Read this file at the start of any task. It indexes the standards of this
repository and the project documentation.

## Standards

The conventions the team decided on. Follow them when you write code. When a
standard conflicts with the task, ask the user.

### Global standards

Located in `docs/standards/global/`.

#### Conventions (`standards/global/conventions.md`)
Commit subjects and pull request titles use the conventional
`type(scope): subject` form, with `!` for a breaking change and the Jira
key first in the subject. A file copied from Apache Cassandra starts with an
SPDX license line and carries no other comment.

### Backend standards

Located in `docs/standards/backend/`.

#### Java conventions (`standards/backend/java-conventions.md`)
palantir-java-format formats every Java file through `ant format`, and
`ant format-check` fails on unformatted code. `ant lint` runs the format
check, Error Prone, Checkstyle, PMD and SpotBugs with the rules in `lint/`,
and a deliberate exception gets a `@SuppressWarnings` on the smallest
element. Every `if`, `else`, `for`, `while`, `do`, `try`, `catch`,
`finally`, `switch` and `synchronized` body has braces and one statement per
line, also for one statement. The formatter runs on JDK 21 and on JDK 25,
the JDK of the CI lint job. No star imports. The code targets Java 21 with `release="21"`, and
CI runs it on JDK 21 and 25. No code or dependency calls `sun.misc.Unsafe`.

### Testing standards

Located in `docs/standards/testing/`.

#### Test writing (`standards/testing/test-writing.md`)
Unit tests use JUnit 6 (Jupiter): package-private classes and methods,
static imports from `org.junit.jupiter.api.Assertions`, `assertThrows` for
exceptions, parameterized tests for input tables and `@TempDir` for files.
The test for a class goes in `test/unit/<package>/<Class>Test.java`, in the
same package. `ant test` runs them all, `-Dtest.name=ClassNameTest` runs one
class, and `ant coverage` writes the JaCoCo report to `build/coverage`.
Integration tests that need ScyllaDB go in `test/integration/<package>/<Name>IT.java`.
They get a Testcontainers node from `ScyllaNode`, run stress in-process with
`CassandraStress`, and run with `ant integration-test`. `ant coverage-all`
reports the unit and integration tests together.
A bug fix lands with a test that fails without it, retry paths get fault
injection, pinned digests guard the generated data, and `PreviousReleaseIT`
validates data that the previous release image wrote.

### Infra standards

Located in `docs/standards/infra/`.

#### CI and build (`standards/infra/ci-and-build.md`)
External GitHub Actions and reusable workflows use a commit SHA pin with a
tag or branch comment. Local references have no pin. Renovate updates the
actions, the Docker base images, and the Scylla driver versions. Update the
other `build.xml` dependencies by hand. Ant is the build tool, and the
`Makefile` holds shortcuts.

## Updating this documentation

- Update a standard when a team convention changes, through
  `/qatools-sdlc:standards-update`.
- Update this index when you add, remove, or change a file.
