## Java conventions

### Code style

Format every Java file with palantir-java-format. Run `./gradlew format`
before a commit. `./gradlew spotlessCheck` fails on a file that is not
formatted, and CI runs it. Do not format a file by hand, and do not argue with the formatter.

    ./gradlew format

The formatter also orders the imports and removes unused ones. Do not use
star imports.

The formatter runs on the newest JDK that CI uses, JDK 25, and on JDK 21. It
parses the code with the `javac` of the JDK that runs it, so keep
palantir-java-format at a release that supports that JDK. CI runs
`./gradlew lint` on JDK 25.

### Braces

Put every body of `if`, `else`, `for`, `while`, `do`, `try`, `catch`,
`finally`, `switch` and `synchronized` in braces, also a body of one
statement. Put the opening brace at the end of the line. Put each statement
of the body on its own line. Put the closing brace on its own line, or before
`else`, `catch`, `finally` or the `while` of a `do`.

```java
if (count == 0) {
    return true;
}
for (String table : tables) {
    truncate(table);
}
```

Do not write `if (count == 0) return true;` or `if (count == 0) { return true; }`.
Checkstyle rejects both with `NeedBraces`, `LeftCurly` and `RightCurly`, and
`./gradlew spotlessCheck` rejects the second.

### Static analysis

Run `./gradlew lint` before a commit. It runs `spotlessCheck`, Error Prone on
the compile, Checkstyle, PMD and SpotBugs, and fails on any finding. The rule
files are in `lint/`. Fix a finding in the code. When the code is right on
purpose, suppress the one check on the smallest element:

    @SuppressWarnings("fallthrough")
    public static long hash64(ByteBuffer key, int offset, int length, long seed)

Use the check name of the tool: `fallthrough` or `EmptyCatch` for Error
Prone, `PMD.CloseResource` for PMD, `checkstyle:MemberName` for Checkstyle.
Add a SpotBugs exclusion to `lint/spotbugs-exclude.xml` with the class and
the bug pattern.

### Java 21

Write code for Java 21. The `cassandra-stress.java-module` convention plugin in
`build-logic` compiles every module with `--release 21` and
`-proc:none`, so a build on JDK 25 still writes Java 21 bytecode. Every LTS
release from 21 runs the code. CI builds and runs the unit tests and the
integration tests on JDK 21 and 25.

### Java modules

Each Gradle subproject in `src/<name>` is one Java module with a `module-info.java`:
`util`, `marshal`, `core` (`org.apache.cassandra.stress`), `driver-v3` and
`driver-v4`. The launcher runs them on the module path.

- Add a package to the `exports` of its module when another module uses it.
- Open a package with `opens ... to <module>` only for the module that
  reflects on it, as `core` opens its root package to SnakeYAML.
- `core` does not depend on a driver module. A driver module provides
  `StressClientProvider` with `provides` in `module-info.java` and with a
  `META-INF/services` file, and `StressClients` finds it with
  `ServiceLoader`. Add a driver as a new module that provides this service.
- Do not add `--add-opens` or `--add-exports` to `conf/jvm-clients.options`.
  A module cannot open a JDK package, so fix the code or the dependency
  instead.
- A dependency jar without a module name gets one in
  `build-logic/src/main/kotlin/org/apache/cassandra/stress/gradle/ModuleJars.kt`. Add a new jar there when the module path
  rejects it.

### No sun.misc.Unsafe

Do not call `sun.misc.Unsafe`, and do not add a dependency that calls it.
JDK 24 and later warn on each call, and a later JDK removes the methods.
Use the `jctools` atomic queues. The JDK 25 integration tests run with
`--sun-misc-unsafe-memory-access=deny`, so an `Unsafe` call fails CI.
