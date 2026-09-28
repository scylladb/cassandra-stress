## Conventions

### Conventional commits

Write commit subjects and pull request titles as `type(scope): subject`.
Use one of these types: `feat`, `fix`, `perf`, `refactor`, `docs`, `test`,
`build`, `ci`, `chore`, `style`, `revert`. The scope is optional. The
release changelog groups pull requests by this type.

    fix(settings): parse remote-dc option in -node

### Apache license header

Start each Java source file with the ASF license block comment, before the
`package` line. Copy the header from an existing file, for example
`src/java/org/apache/cassandra/stress/StressAction.java`.
