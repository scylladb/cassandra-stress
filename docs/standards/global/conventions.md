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

Start a file that you copy from Apache Cassandra with one line, and add no
other comment. The repository allows no comments, and an SPDX line is a
license directive. `NOTICE.txt` keeps the Apache Cassandra attribution. A
file that already has the ASF header keeps it until you rewrite the file.

    // SPDX-License-Identifier: Apache-2.0
    package org.apache.cassandra.stress.marshal;
