# QATOOLS-123 — Remove the vendored Cassandra source from cassandra-stress

## Problem

The repository ships most of the Apache Cassandra 3.x server tree next to
cassandra-stress. The stress tool is 139 files in `org.apache.cassandra.stress`.
The main source tree `src/java` holds 1,658 files. The other 1,519 files are
server code: `db`, `cql3`, `io`, `service`, `streaming`, `thrift`, `tools`,
`hints`, `repair` and more. The `test/` tree holds 1,125 test classes, and only
4 of them test stress.

Stress imports 146 distinct classes from the server tree. These include
`DatabaseDescriptor`, `ColumnFamilyStore`, `StorageService`, `SSTableReader`,
`TokenMetadata`, `NodeProbe` and `QueryProcessor`. Through these imports,
stress reaches 1,549 of the 1,568 non-stress classes. So a change to the stress
tool cannot remove any server code, and each JDK or dependency upgrade must
keep the full server tree compiling.

The build does not complete on a current JDK. On JDK 25, `ant build-test`
compiles the classes and then fails. The `compile-command-annotations`
processor wrote `META-INF/hotspot_compiler`, and `build.xml` copies that file.
Since JDK 23, javac does not run annotation processors from the classpath
unless the build asks for it. So the file is missing and the copy fails. The
same command on JDK 21 succeeds.

The build also carries server-only baggage: jamm as a `-javaagent`, sigar with
a native library from the apache/cassandra GitHub repository, ecj, byteman,
ohc, jarjar, two Maven resolvers, and a list of `--add-exports` and
`--add-opens` flags for JDK internals. The server code uses `sun.misc.Unsafe`,
a `SecurityManager` subclass that calls `System.setSecurityManager`, and
reflection on `jdk.internal.module.IllegalAccessLogger`.

The `next` branch removes the server tree and moves to Gradle. It is one large
change: 3,438 files, −624,388 lines. It has no tests, it stubs out
`JmxCollector`, and its CI workflows still run Ant on Java 21. Nobody has
tested it.

## Who it affects

- Maintainers of cassandra-stress. Each JDK or dependency upgrade must keep
  about 1,500 unused server classes compiling.
- Scylla testing infrastructure (SCT and the Docker image). These must stay on
  JDK 21 until the build works on a newer JDK.
- Renovate. It cannot update the server-era dependencies, because the
  Cassandra 3.x code holds them at old versions.
- New contributors, who must find 139 stress files among 1,658.

## Evidence

Jira QATOOLS-123, "Remove unneeded Cassandra source code for c-s":

> Keeping Cassandra source code inside c-s: Blocks or complicates Java version
> upgrades. Makes switching to a more modern and maintainable build system
> harder. Breaks or limits Renovate and dependency automation.

> Importantly, this task does not remove Cassandra compatibility or support.
> cassandra-stress will continue to work against Cassandra clusters exactly as
> it does today.

`ant -Dsource.version=25 -Dtarget.version=25 -Drelease.version=25 build-test`
on Temurin 25.0.2, master at `3f8a9b79a3`:

```
    [javac] warning: [options] --add-opens has no effect at compile time
    [javac] src/java/org/apache/cassandra/io/util/Memory.java:52: warning: [removal] arrayBaseOffset(Class<?>) in Unsafe has been deprecated and marked for removal
    [javac] only showing the first 100 warnings, of 133 total; use -Xmaxwarns if you would like to see more

BUILD FAILED
build.xml:1117: Warning: Could not find file build/classes/main/META-INF/hotspot_compiler to copy.
```

`ant build-test` on Temurin 21.0.11, same commit:

```
    [javac] Note: Writing compiler command file at META-INF/hotspot_compiler
BUILD SUCCESSFUL
```

CI builds and tests on one JDK only. `.github/workflows/test.yml` sets the
Java matrix to `["21"]`, and the `Dockerfile` builds on
`eclipse-temurin:21.0.12_8-jdk-noble`.

## What good looks like

- The repository holds only the cassandra-stress source and the small set of
  helpers it needs. Stress imports no server class, such as
  `DatabaseDescriptor`, `ColumnFamilyStore`, `StorageService` or `NodeProbe`.
- The `cassandra-stress` command is the only tool in the repository. The
  offline tools that run server code are gone: `CompactionStress`, the
  offline SSTable path of `userdefined/SchemaInsert`, and
  `StressCQLSSTableWriter`. The online insert path of `SchemaInsert` stays.
- The Thrift client mode is gone, with the generated classes in
  `interface/thrift/gen-java`. Stress connects through the native protocol
  only. The Thrift mode needs the server class `ThriftConversion`, and
  Cassandra 4.0 removed the Thrift protocol.
- JMX is gone: the collector `stress/util/JmxCollector`, `-port jmx=`, the
  GC columns and the GC summary lines.
- The stress code keeps the `org.apache.cassandra.stress` package and the
  main class `org.apache.cassandra.stress.Stress`.
- The build and the tests succeed on JDK 21 and 25. The CI test matrix runs
  both and proves it.
- The jar holds Java 21 bytecode, so stress still runs on a JDK 21 runtime.
- The Docker image and the deb and rpm packages build and run as today.
- The integration tests in `integration-tests/` pass against Scylla with the
  3.x and 4.x drivers, as they do today.
- SCT moves to the new command line and output when it bumps the image.
  The removed options are `-mode thrift`, `-mode simplenative`,
  `-port thrift=`, `-port jmx=`, `-transport factory=`, `-col super=` and
  `-col comparator=`. The `simplenative` mode runs on the server class
  `transport.SimpleClient`.
  `-schema replication(strategy=X)` also fails for `SimpleStrategy`,
  `LocalStrategy` and `OldNetworkTopologyStrategy`. Two SCT provision tests
  against Cassandra use `SimpleStrategy`, and SCT moves them to
  `NetworkTopologyStrategy` before it bumps the image. Only system keyspaces
  use `LocalStrategy`, and Cassandra 4.0 removed `OldNetworkTopologyStrategy`.
- The build uses no server-only dependency or JVM flag: jamm, sigar, ecj,
  byteman, ohc, or the `--add-exports` and `--add-opens` flags for server code.

## Out of scope

- The move to Gradle. It is a second pull request after this one merges.
- JDK 27 in CI, and a JDK 25 runtime for the Docker image and the packages.
- Changes to stress features to use new Java features, such as virtual
  threads or records. Follow-up tasks cover these.
- Removal of Cassandra support. Stress continues to run against Cassandra
  and Scylla clusters.
- A merge of the `next` branch as it is. It is a source of ideas, not a base.
