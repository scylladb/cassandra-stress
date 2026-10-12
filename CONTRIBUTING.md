# Contributing to cassandra-stress

## Before you write code

1. Open an issue in the `QATOOLS` Jira project.
2. Read `docs/INDEX.md` and the standards in `docs/standards/`.
3. Write the task artifacts in `tasks/<KEY>/` and commit each one before the code that uses it.

## Build and check

Use Ant 1.10.17 or later and JDK 21 or JDK 25.

| Command | Result |
|---|---|
| `ant build-test` | Compiles the main and the test code |
| `ant test` | Runs the unit tests |
| `ant integration-test` | Runs the integration tests against ScyllaDB in Docker |
| `ant format` | Formats the Java code with palantir-java-format |
| `ant lint` | Runs the format check, Error Prone, Checkstyle, PMD and SpotBugs |

Run `ant format`, `ant lint` and `ant test` before each commit.

## Code style

The formatter sets the layout. `docs/standards/backend/java-conventions.md` holds the other rules, such as braces on every `if`, `for` and `while` body.

## Commits and pull requests

Write commit subjects as `type(scope): KEY subject`, as `docs/standards/global/conventions.md` states. Open the pull request against `master` and end its description with `closes KEY` or `refs KEY`.
