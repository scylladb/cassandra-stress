# Documentation Index

Read this file at the start of any task. It indexes the standards of this
repository and the project documentation.

## Standards

The conventions the team decided on. Follow them when you write code. When a
standard conflicts with the task, ask the user.

### Global standards

Located in `docs/standards/global/`.

#### Conventions (`standards/global/conventions.md`)
Conventional commit subjects and pull request titles in the
`type(scope): subject` form, and the Apache license header at the start of
each Java source file.

### Backend standards

Located in `docs/standards/backend/`.

#### Java conventions (`standards/backend/java-conventions.md`)
Allman braces, four-space indentation without tabs, the import order with
`java.*` and `javax.*` first, try-with-resources for every `AutoCloseable`,
and Java 21 as the target version.

### Testing standards

Located in `docs/standards/testing/`.

#### Test writing (`standards/testing/test-writing.md`)
JUnit 4 tests with `@Test` and the static `org.junit.Assert.*` import, and
the test location `test/unit/<package>/<Class>Test.java` in the same package
as the class under test.

### Infra standards

Located in `docs/standards/infra/`.

#### CI and build (`standards/infra/ci-and-build.md`)
GitHub Actions pinned to a commit SHA with a release tag comment, Renovate
for dependency updates with `chore(deps):` subjects, and Ant as the build
tool with `Makefile` targets that call Ant.

## Updating this documentation

- Update a standard when a team convention changes, through
  `/qatools-sdlc:standards-update`.
- Update this index when you add, remove, or change a file.
