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
The code style follows `ide/idea/codeStyleSettings.xml`: braces on their own
line, four-space indentation, no tabs. A file with K&R braces keeps its own
style. Imports use four groups, with static imports last. The code targets
Java 21 with `release="21"`, and CI runs it on JDK 21 and 25. No code or
dependency calls `sun.misc.Unsafe`.

### Testing standards

Located in `docs/standards/testing/`.

#### Test writing (`standards/testing/test-writing.md`)
Unit tests use JUnit 4 with `@Test` and static imports from
`org.junit.Assert`. The test for a class goes in
`test/unit/<package>/<Class>Test.java`, in the same package. `ant test`
runs them all, and `-Dtest.name=ClassNameTest` runs one class.

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
