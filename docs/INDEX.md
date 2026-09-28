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
`type(scope): subject` form, and the license header rule: keep the ASF
header in inherited files, do not add it to new files.

### Backend standards

Located in `docs/standards/backend/`.

#### Java conventions (`standards/backend/java-conventions.md`)
The code style of `ide/idea/codeStyleSettings.xml` (braces on their own
line, four-space indentation, no tabs), the rule to keep the style of a K&R
file when you edit it, the four-group import order with static imports last,
and Java 21 as the target version.

### Testing standards

Located in `docs/standards/testing/`.

#### Test writing (`standards/testing/test-writing.md`)
JUnit 4 tests with `@Test` and static imports from `org.junit.Assert`, and
the test location `test/unit/<package>/<Class>Test.java` in the same package
as the class under test.

### Infra standards

Located in `docs/standards/infra/`.

#### CI and build (`standards/infra/ci-and-build.md`)
GitHub Actions and reusable workflows pinned to a commit SHA with a tag or
branch comment, the scope of Renovate updates and the dependencies to update
by hand, and Ant as the build tool with `Makefile` targets that call Ant.

## Updating this documentation

- Update a standard when a team convention changes, through
  `/qatools-sdlc:standards-update`.
- Update this index when you add, remove, or change a file.
