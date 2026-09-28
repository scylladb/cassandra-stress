## Conventions

### Conventional commits

Write commit subjects and pull request titles as `type(scope): subject`.
Use one of these types: `feat`, `fix`, `perf`, `refactor`, `docs`, `test`,
`build`, `ci`, `chore`, `style`, `revert`. The scope is optional. Mark a
breaking change with `!` after the type or the scope. When the work has a
Jira issue, put the key at the start of the subject.

    fix(settings): QATOOLS-123 parse remote-dc option in -node
    feat(mode)!: remove the thrift mode

### License header

Keep the ASF license header in the files that have it. These files come
from Apache Cassandra.
