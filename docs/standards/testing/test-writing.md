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
void removedOptionsAreRejected(String param) {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(param));
    assertEquals("Invalid parameter " + param, e.getMessage());
}
```

### Test location

Put the test for `<module>/src/main/java/<package>/<Class>.java` in
`<module>/src/test/java/<package>/<Class>Test.java`, in the same module. The
test class uses the same package as the class under test. A core test that
needs a driver client uses the driver module as a test dependency.

### Integration tests

Put a test that needs ScyllaDB in
`src/core/src/integrationTest/java/<package>/<Name>IT.java`.
Get the node from `ScyllaNode`. It starts one Testcontainers ScyllaDB
container for the whole run, and `-Dscylla.image=` selects the image. Run
stress in-process with `CassandraStress`, which adds `-node`, `-port` and
`-log file=`. Check the outcome on the returned `StressResult`.
Give each test its own keyspace, and drop it before the run.
`./gradlew integrationTest` runs every class on both drivers. Select one
driver with `-PintegrationDriver=native` or `-PintegrationDriver=4x`.
The run sets `-Dstress.skipSleeps=true`, which skips the fixed sleeps of
stress. CI runs these tests on a pull request with the `testcontainers`
label, and on each push to `master`.

```java
StressResult write = new CassandraStress(dir).run("write", "n=2000", "-schema", "keyspace=" + keyspace, REPLICATION);
assertTrue(write.succeeded(), write::toString);
assertEquals(2000, ScyllaNode.count(keyspace, "standard1"));
```

### Distribution and package tests

`DistributionIT` runs `bin/cassandra-stress` from the tree that
`./gradlew installDist` writes, so it tests the launcher and the module path.
Put a workload that a user starts from the command line there.
`PackageIT` installs the deb or the rpm with a Temurin runtime in a clean
distribution image and runs `cassandra-stress version`.
`-Dstress.package.images=` and `-Dstress.package.java=` select the images and
the Java versions. The `integrationTest` tasks skip both classes.

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

Run all unit tests with `./gradlew test`. Run one class with its simple name
in the module that holds it.
Run `./gradlew integrationTest` to run the integration tests. It needs Docker.
Run `./gradlew distributionTest` and `./gradlew packageTest` for the launcher
and the packages.
Run `./gradlew jacocoTestReport` for a JaCoCo report of the unit tests, or
`./gradlew jacocoFullReport` for one report of the unit and integration tests.
The reports go to `build/reports/jacoco`. CI runs `./gradlew test` and
`./gradlew jacocoFullReport` on JDK 21 and 25, and uploads the report as an
artifact.

    ./gradlew :util:test --tests CqlNamesTest
