# QATOOLS-123 — Remove the vendored Cassandra source from cassandra-stress

**Date**: 2026-09-30

## Design drivers

- Generated data stays byte-identical. `PartitionIterator` seeds each row from the serialized key bytes (`seed(type.decompose(object), ...)`). A new release must validate data that an old release wrote, so every serializer keeps its byte format.
- A command line that uses a removed option fails at argument parsing, before stress connects to the cluster. It does not run with part of its options ignored.
- This pull request removes code and ports helpers. JDK 25 runs the build and the tests, to prove that the removal clears the JDK 25 build failure. Both drivers must run on JDK 25, and a driver failure there is fixed in this pull request. The jar keeps Java 21 bytecode.
- The Gradle move follows in a second pull request. The Ant build keeps to plain Maven coordinates plus the driver 4.x shade, so the Gradle build can copy the dependency list as-is.

## Goals

- Stress, its helpers and its tests are the only Java source in the repository.
- The helpers that stress needs move under `org.apache.cassandra.stress`, rewritten or trimmed from the Cassandra originals. A file that comes from a Cassandra original keeps its ASF license header.
- A unit test compares the serialized bytes of each `stress.marshal` type with fixed bytes that master produces.
- `-mode` keeps `native` (driver 3.x) and `native 4x` (driver 4.x) only.
- `build.xml` declares only the direct dependencies of stress, and keeps only the targets that CI, the Makefile, the Dockerfile and packaging call.
- The build, the stress unit tests and the integration tests pass on JDK 21 and 25. The CI test matrix is `["21", "25"]`.

## Non-goals

- The Gradle build (the second pull request).
- JDK 27 in CI, a `--release` above 21, or a JDK 25 base for the Docker image, the deb and rpm packages, or `release.yml`.
- A rename of the `org.apache.cassandra.stress` package or of the main class.
- Changes to workloads, generators, distributions, or the output format.
- Replacements for the removed modes: Thrift, `simplenative`, offline SSTable writing, `CompactionStress`, and JMX.
- New tests beyond the four stress unit tests that exist today and the `stress.marshal` byte-format test.

## Design

### What changes for the user

| Area | Today | After |
|---|---|---|
| `-mode` | `native`, `native 4x`, `cql3 simplenative`, `thrift [smart]` | `native`, `native 4x` |
| `-mode thrift`, `-mode cql3 simplenative` | Run | Stop at argument parsing: `Mode <name> was removed. Use -mode native or -mode native 4x.` |
| `-port` | `native=`, `thrift=`, `jmx=` | `native=`. The other two stop at argument parsing with the usual unknown-option error |
| `-transport` | `factory=` and the SSL options | The SSL options. `factory=` stops at argument parsing |
| `-schema replication(strategy=X)` | Any class on the classpath that extends `AbstractReplicationStrategy` | `NetworkTopologyStrategy` or `EverywhereStrategy`, short or full name. Other names stop with `Invalid replication strategy: X` |
| `-schema compaction(strategy=X)` | Any compaction class that `CFMetaData` loads | The five compaction classes of the vendored tree. Other names stop with `Invalid compaction strategy: X` |
| GC columns and `Total GC` lines | Values over JMX, or zero when JMX fails | Zero |
| `CompactionStress`, offline `SchemaInsert` | Write SSTables with server code | Removed |
| User profiles, other commands, workloads, output | | Unchanged |

`SimpleStrategy` leaves the replication allow-list, because our tests never use it and Scylla rejects it for tablets keyspaces. `LocalStrategy` leaves because only system keyspaces use it, and `OldNetworkTopologyStrategy` leaves because Cassandra 4.0 removed it. A profile that names a strategy in its own `CREATE KEYSPACE` text passes it to the cluster unchanged.

The user profile flow does not change. `StressProfile` creates the keyspace and the table, then reads the table through the `MetadataProvider` that `JavaDriverClient` and `JavaDriverV4Client` implement today. The generators bind to the driver columns, as today. The parse of the profile CQL to get the keyspace and the table name moves from `CQLFragmentParser` to `stress.util.CqlNames`.

### What changes in the code

| Server dependency | Used by | Result |
|---|---|---|
| `thrift.*`, `interface/thrift/gen-java` | Thrift mode | Removed |
| `transport.SimpleClient`, `ResultMessage` | `simplenative` mode | Removed |
| `ColumnFamilyStore`, `StressCQLSSTableWriter`, `CFMetaData`, `QueryProcessor`, `CreateTableStatement` | offline `SchemaInsert`, `CompactionStress` | Removed |
| `tools.NodeProbe` | `JmxCollector` | Removed |
| `CQLFragmentParser`, `CqlParser` | `StressProfile` names | `stress.util.CqlNames` |
| `db.marshal.*`, `serializers.*`, `TypeParser` | generators, `PartitionIterator`, `-col comparator=` | Ported to `stress.marshal`, byte format unchanged |
| `AbstractReplicationStrategy`, `CFMetaData.createCompactionStrategy` | `OptionReplication`, `OptionCompaction` | `ReplicationStrategy` and `CompactionStrategy` allow-lists |
| `DatabaseDescriptor.clientInitialization` | `Stress` | Removed |
| `ByteBufferUtil`, `FBUtilities`, `Pair`, `UUIDGen`, `MurmurHash`, `DynamicList`, `LockedDynamicList`, `ConsistencyLevel`, `EncryptionOptions`, `SSLFactory`, `FileUtils` | many | Trimmed copies in `stress.util` |
| `WindowsTimer`, `NamedThreadFactory` | `Stress`, `StressServer` | Removed, or replaced with JDK classes |

### Build and dependencies

`build.xml` declares the jars that stress code imports, plus the logging and compression jars that the drivers load at run time. The resolver brings the transitive dependencies from the driver POMs. One POM, `cassandra-stress`, replaces the `parent`, `all` and `thrift` POMs.

| Scope | Coordinates |
|---|---|
| runtime | `scylla-driver-core` 3.x, `java-driver-core` 4.x (shaded by jarjar), guava, commons-math3, commons-lang3, commons-cli, snakeyaml, json-simple, jctools-core, netty-common, slf4j-api, logback-classic, lz4-java, snappy-java |
| test | junit 4, hamcrest |
| build | `maven-resolver-ant-tasks`, jarjar |

Every other coordinate goes, `compile-command-annotations` included. `<javac>` sets `-proc:none`, so no JDK writes `META-INF/hotspot_compiler`. The `build` target loses its copy of that file at `build.xml:1117`, and the `artifacts` target loses the exclude at `build.xml:1205`. `conf/jvm-clients.options` loses the flags that only server code needs. The integration tests on JDK 21 and 25 decide the list.

`build.xml` keeps `init`, `clean`, `realclean`, the resolver targets, `java-driver-core.get`, `java-driver-core.shade`, `scylla-driver-core.override`, `build`, `jar`, `artifacts`, `build-test`, `testold` and `testsome`. All other targets go, with the properties that only they read.

These files go with the code that used them: `interface/`, `src/antlr/`, `src/gen-java/`, `src/java/com/datastax/`, `test/distributed`, `test/long`, `test/burn`, `test/microbench`, `test/data`, the non-stress tests in `test/unit`, `.build/dependency-check-suppressions.xml`, `eclipse_compiler.properties`, `ide/idea-iml-file.xml`, `NEWS.txt` and `README-cassandra.asc`. `ide/idea/` stays, because the Java standard reads its code style.

## Contracts

### Inputs

The command line, after this change. Every other option is unchanged.

```
-mode native [4x] cql3 [prepared|unprepared] [protocolVersion=N] [compression=none|lz4|snappy] [user= password= ...]
-port native=9042
-transport [truststore= keystore= ssl-protocol= ssl-ciphers= ...]
-schema replication(strategy=NetworkTopologyStrategy|EverywhereStrategy ...) compaction(strategy=<one of five> ...)
```

User profiles keep their YAML format.

### Outputs

The interval and summary output keep today's header. The GC fields print zero:

```
type, total ops, op/s, pk/s, row/s, mean, med, .95, .99, .999, max, time, stderr, errors, gc: #, max ms, sum ms, sdv ms, mb
```

The distribution keeps `bin/cassandra-stress`, `conf/`, `lib/` and the jar name. The launcher classpath drops `$classes/thrift`.

### Module API

```java
package org.apache.cassandra.stress.marshal;

public abstract class AbstractType<T> {
    public ByteBuffer decompose(T value);
    public T compose(ByteBuffer bytes);
    public String getString(ByteBuffer bytes);
    public static AbstractType<?> parse(String typeName);
}

package org.apache.cassandra.stress.settings;

public enum ReplicationStrategy { NetworkTopologyStrategy, EverywhereStrategy; public static String validate(String name); }
public enum CompactionStrategy {
    SizeTieredCompactionStrategy, LeveledCompactionStrategy, TimeWindowCompactionStrategy,
    DateTieredCompactionStrategy, IncrementalCompactionStrategy;
    public static String validate(String name);
}
```

`AbstractType.parse` reads the type names that `-col comparator=` accepts: `AsciiType`, `UTF8Type` and `TimeUUIDType`. `ReplicationStrategy.validate` returns the full `org.apache.cassandra.locator.` name, and `CompactionStrategy.validate` returns the name as given, as today.

## Subtasks

None. One pull request carries this spec, because stress does not compile until the removals and the ports are both in. The Gradle move is a second pull request under the same key.

## Risks

| Risk | Response |
|---|---|
| A ported serializer changes the bytes, and validation of old data fails | Port the serializer bodies as-is. The `stress.marshal` byte-format test fixes the bytes that master produces for each type |
| SCT passes a removed option, such as `-mode thrift`, `-port jmx=` or `-transport factory=` | Search SCT for the removed options before merge, and change SCT first where it uses one |
| Without the hand-pinned transitive jars, the resolver picks other versions of Netty, Guava or Jackson for the drivers | Compare the `build/lib/jars` list against master in the plan. Pin a version only when the integration tests or a CVE require it |
| Driver 3.x or 4.x, or the Netty in them, fails on JDK 25 | Fix it in this pull request. Add the JVM flags that the JDK 25 integration tests show to be needed, or move to a driver version that runs on JDK 25. The JDK 25 matrix entry blocks a merge |
| The diff is too large to review | Remove files in separate commits (Thrift, simplenative, offline and JMX, server tree, server tests) before the rewrite commits |

## Deferred work

- A JDK 25 runtime for the Docker image and the packages. JDK 27 in CI follows after its GA.
- The Gradle build. The source stays in `src/java` and `test/unit`, and the Gradle pull request moves it to `src/main/java` and `src/test/java`.
- Adoption of new Java features. That needs a `--release` bump and ends JDK 21 runtime support.

---

Files, internal functions, tests, and line numbers go to `plan.md`. On the
spike path the code diff carries them, and the spec keeps this shape. A spec
near 150 lines, diagrams included, reads in one sitting. Above that, consider
a split and propose it in the spec. This footer stays in every spec.
