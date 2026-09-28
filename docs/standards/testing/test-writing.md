## Test writing

### JUnit 4

Write unit tests with JUnit 4. Mark each test method with `@Test`. Use the
static import `import static org.junit.Assert.*`.

### Test location

Put the test for `src/java/<package>/<Class>.java` in
`test/unit/<package>/<Class>Test.java`. The test class uses the same
package as the class under test.
