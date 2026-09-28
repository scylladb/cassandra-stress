## Java conventions

### Allman braces

Put the opening brace of a class, method, and control block on its own
line.

    public void run()
    {
        output.println("Sleeping 2s...");
    }

### Indentation

Indent with four spaces. Do not use tabs.

### Import order

Put `java.*` and `javax.*` imports first. Put the project and third-party
imports after them. Separate the groups with one blank line.

### Try-with-resources

Close every `AutoCloseable` with try-with-resources. The Eclipse compiler
settings in `eclipse_compiler.properties` treat an unclosed resource as an
error.

### Java 21

Write code for Java 21. CI builds and tests on Java 21 only.
