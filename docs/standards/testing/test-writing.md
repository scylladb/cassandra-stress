## Test writing

### JUnit Jupiter

Write unit tests with JUnit 6 (Jupiter). Make the test class and its methods
package-private. Import the assertions statically from
`org.junit.jupiter.api.Assertions`.

- Check an exception with `assertThrows`, and check its message on the
  returned exception.
- Use `@ParameterizedTest` with `@ValueSource`, `@CsvSource`, `@EnumSource` or
  `@MethodSource` when one rule holds for many inputs.
- Use `@TempDir` for files. Do not delete temporary files by hand.

```java
@ParameterizedTest
@ValueSource(strings = { "super=1", "comparator=UTF8Type" })
void removedOptionsAreRejected(String param)
{
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(param));
    assertEquals("Invalid parameter " + param, e.getMessage());
}
```

### Test location

Put the test for `src/java/<package>/<Class>.java` in
`test/unit/<package>/<Class>Test.java`. The test class uses the same
package as the class under test.

### Integration tests

Put a test that needs ScyllaDB in `test/integration/<package>/<Name>IT.java`.
Get the node from `ScyllaNode`. It starts one Testcontainers ScyllaDB
container for the whole run, and `-Dscylla.image=` selects the image. Run
stress in-process with `CassandraStress`, which adds `-node`, `-port` and
`-log file=`. Check the outcome on the returned `StressResult`.
Give each test its own keyspace, and drop it before the run.

```java
StressResult write = new CassandraStress(dir).run("write", "n=2000", "-schema", "keyspace=" + keyspace, REPLICATION);
assertTrue(write.succeeded(), write::toString);
assertEquals(2000, ScyllaNode.count(keyspace, "standard1"));
```

### Regression tests

A bug fix lands with a test that fails without the fix. Run the test once
against the old code before the commit. A retry or error path gets a test
that injects the failure, such as a driver session that fails every other
statement (`RetryIT`).

`GeneratedDataCompatibilityTest` and `PartitionIteratorTest` pin a digest of
the rows that stress generates. Change a pinned digest only with a spec
decision, because stored data from an older release stops validating.
`PreviousReleaseIT` writes with the previous release image
(`-Dstress.previous.image=`) and validates the rows with the new build.

### Running the tests

Run all unit tests with `ant test`. Run one class with its simple name.
Run `ant integration-test` to run the integration tests. It needs Docker.
Run `ant coverage` for a JaCoCo report of the unit tests, or `ant
coverage-all` for one report of the unit and integration tests. The report
goes to `build/coverage/html`. CI runs `ant test` and `ant coverage-all` on
JDK 21 and 25, and uploads the report as an artifact.

    ant test -Dtest.name=CqlNamesTest
