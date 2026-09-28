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
in `stress/core/`. When you edit such a file, keep the style of that file.
Do not reformat lines that your change does not touch.

### Import order

Use the import layout of `ide/idea/codeStyleSettings.xml`. Separate the
groups with one blank line:

1. `java.*` and `javax.*`
2. `com.google.common`, `org.apache.log4j`, `org.apache.commons`,
   `org.cliffc.high_scale_lib`, `org.junit`, `org.slf4j`
3. All other imports
4. Static imports

### Java 21

Write code for Java 21. `build.xml` sets the source and target version to
21. CI compiles the code and the unit tests on Java 21. CI does not run the
unit tests, so run them locally.
