## Java conventions

### Code style

Follow `ide/idea/codeStyleSettings.xml`. Put the opening brace of a class,
method, and control block on its own line. Put `else`, `catch`, and
`finally` on a new line. Indent with four spaces. Do not use tabs.

    public void run()
    {
        output.println("Sleeping 2s...");
    }

Some files use K&R braces and two-space indentation, for example the files
in `src/java/org/apache/cassandra/stress/core/`. When you edit such a file,
keep the style of that file. Do not reformat lines that your change does not
touch.

### Import order

Use the import layout of `ide/idea/codeStyleSettings.xml`. Separate the
groups with one blank line:

1. `java.*` and `javax.*`
2. `com.google.common`, `org.apache.log4j`, `org.apache.commons`,
   `org.cliffc.high_scale_lib`, `org.junit`, `org.slf4j`
3. All other imports
4. Static imports

### Java 21

Write code for Java 21. `build.xml` compiles with `release="21"` and
`-proc:none`, so a build on JDK 25 still writes Java 21 bytecode. CI builds
and runs the unit tests and the integration tests on JDK 21 and 25.

### No sun.misc.Unsafe

Do not call `sun.misc.Unsafe`, and do not add a dependency that calls it.
JDK 24 and later warn on each call, and a later JDK removes the methods.
Use the `jctools` atomic queues. The JDK 25 integration tests run with
`--sun-misc-unsafe-memory-access=deny`, so an `Unsafe` call fails CI.
