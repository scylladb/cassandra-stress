# QATOOLS-123 — Remove the vendored Cassandra source from cassandra-stress

## Overview

The change deletes the Cassandra server tree and the stress features that run on it: the Thrift mode, `simplenative`, offline SSTable writing, `CompactionStress` and JMX. The server helpers that the remaining stress code needs move under `org.apache.cassandra.stress` with their byte format unchanged. The user profile flow stays on the driver metadata it uses today. `build.xml` keeps only the direct dependencies of stress. CI runs the build and the tests on JDK 21 and 25, and the jar keeps Java 21 bytecode.

## Constraints

- Generated data stays byte-identical. `PartitionIterator` seeds each row from the serialized key bytes, so a new release must produce the bytes that an old release wrote.
- A command line that uses a removed option fails at argument parsing, before stress connects to the cluster.
- Both drivers run on JDK 25. A driver failure on JDK 25 blocks the merge of this pull request.
- `build.xml` uses plain Maven coordinates plus the driver 4.x shade, so the Gradle build can copy the dependency list as-is.

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

A profile that names a strategy in its own `CREATE KEYSPACE` text passes it to the cluster unchanged.

The user profile flow stays as it is. `StressProfile` creates the keyspace and the table, then reads the table through the `MetadataProvider` that `JavaDriverClient` and `JavaDriverV4Client` implement. The generators bind to the driver columns. Only the parse of the keyspace and the table name from the profile CQL moves, from `CQLFragmentParser` to `stress.util.CqlNames`.

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

A file that comes from a Cassandra original keeps its ASF license header. A unit test compares the serialized bytes of each `stress.marshal` type with fixed bytes that master produces.

### Build and CI

`build.xml` declares the jars that stress code imports, plus the logging and compression jars that the drivers load at run time. The resolver brings the transitive dependencies from the driver POMs. One POM, `cassandra-stress`, replaces the `parent`, `all` and `thrift` POMs.

| Scope | Coordinates |
|---|---|
| runtime | `scylla-driver-core` 3.x, `java-driver-core` 4.x (shaded by jarjar), guava, commons-math3, commons-lang3, commons-cli, snakeyaml, json-simple, jctools-core, netty-common, slf4j-api, logback-classic, lz4-java, snappy-java |
| test | junit 4, hamcrest |
| build | `maven-resolver-ant-tasks`, jarjar |

Every other coordinate goes, `compile-command-annotations` included. `<javac>` sets `--release 21` and `-proc:none`. With no annotation processor, the `build` target stops copying `META-INF/hotspot_compiler`, and the `artifacts` target stops excluding it. `conf/jvm-clients.options` keeps only the flags that the drivers need. The integration tests on JDK 21 and 25 decide that list.

`build.xml` keeps the targets that CI, the Makefile, the Dockerfile and packaging call: `init`, `clean`, `realclean`, the resolver targets, `java-driver-core.get`, `java-driver-core.shade`, `scylla-driver-core.override`, `build`, `jar`, `artifacts`, `build-test`, `testold` and `testsome`. The CI test matrix is `["21", "25"]`.

The server tree, the server tests, the Thrift and ANTLR sources, and the build files that only they use leave the repository. `ide/idea/` stays, because the Java standard reads its code style.

## Contracts

### Inputs

The command line, after this change. Every other option keeps its form.

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

## Risks

| Risk | Response |
|---|---|
| A ported serializer changes the bytes, and validation of old data fails | Port the serializer bodies as-is. The `stress.marshal` byte-format test fixes the bytes that master produces for each type |
| SCT passes a removed option, such as `-mode thrift`, `-port jmx=` or `-transport factory=` | Search SCT for the removed options before merge, and change SCT first where it uses one |
| Without the hand-pinned transitive jars, the resolver picks other versions of Netty, Guava or Jackson for the drivers | Compare the `build/lib/jars` list against master in the plan. Pin a version only when the integration tests or a CVE require it |
| Driver 3.x or 4.x, or the Netty in them, fails on JDK 25 | Add the JVM flags that the JDK 25 integration tests show to be needed, or move to a driver version that runs on JDK 25 |
| The diff is too large to review | Remove files in separate commits (Thrift, simplenative, offline and JMX, server tree, server tests) before the rewrite commits |

## Deferred work

- The Gradle build, in a second pull request under the same key. It moves the source from `src/java` and `test/unit` to `src/main/java` and `src/test/java`.
- A JDK 25 runtime for the Docker image and the packages.
- JDK 27 in CI, after its GA.
- New Java features in stress code. They need a `--release` above 21 and end JDK 21 runtime support.

## Decisions

- Thrift, `simplenative`, offline SSTable writing, `CompactionStress` and JMX go with no replacement. Each one runs on server code, and Cassandra 4.0 removed Thrift. (spec)
- The GC fields stay in the output and print zero, so parsers of the output keep working. (spec)
- The replication allow-list leaves out `SimpleStrategy`, `LocalStrategy` and `OldNetworkTopologyStrategy`. Our tests never use `SimpleStrategy`, only system keyspaces use `LocalStrategy`, and Cassandra 4.0 removed `OldNetworkTopologyStrategy`. (review)
- One pull request carries the removal, because stress does not compile until the removals and the ports are both in. (spec)
- CI tests on JDK 21 and 25, and this pull request fixes any driver failure on JDK 25. (review)
- JDK 27 joins CI after its GA. (review)
- A unit test fixes the bytes of each ported serializer against master. (review)
