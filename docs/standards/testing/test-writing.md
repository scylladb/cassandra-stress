## Test writing

### JUnit 4

Write unit tests with JUnit 4. Mark each test method with `@Test`. Import
the assertions statically from `org.junit.Assert`.

### Test location

Put the test for `src/java/<package>/<Class>.java` in
`test/unit/<package>/<Class>Test.java`. The test class uses the same
package as the class under test.

### Running the tests

Run all unit tests with `ant test`. Run one class with its simple name.
CI runs `ant test` on JDK 21 and 25.

    ant test -Dtest.name=CqlNamesTest
