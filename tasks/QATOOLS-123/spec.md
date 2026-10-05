# QATOOLS-123 — Remove the vendored Cassandra source from cassandra-stress

## Overview

The change deletes the Cassandra server tree and the stress features that run on it: the Thrift mode, `simplenative`, offline SSTable writing, `CompactionStress`, JMX with the GC output, and the Thrift-era `-col super=` and `comparator=` options, and the `legacy` command. SCT moves to the new command line and output when it bumps the image. The server helpers that the remaining stress code needs move under `org.apache.cassandra.stress` with their byte format unchanged. The user profile flow stays on the driver metadata it uses today. `build.xml` keeps only the direct dependencies of stress. CI runs the build and the tests on JDK 21 and 25, and the jar keeps Java 21 bytecode.

## Constraints

- Generated data stays byte-identical. `PartitionIterator` seeds each row from the serialized key bytes, so a new release must produce the bytes that an old release wrote.
- A command line that uses a removed option fails at argument parsing, before stress connects to the cluster.
- The driver runs on JDK 25. A driver failure on JDK 25 blocks the merge of this pull request.
- `build.xml` uses plain Maven coordinates only, so the Gradle build can copy the dependency list as-is.

## Design

### What changes for the user

| Area | Today | After |
|---|---|---|
| `-mode` | `cql3 native`, `cql3 4x`, `cql3 simplenative`, `thrift [smart]` | `cql3 native`, `cql3 4x` |
| `-mode thrift` | Run | Stops at argument parsing: `Invalid parameter thrift` |
| `-mode cql3 simplenative` | Run | Stops at argument parsing: `Mode simplenative was removed. Use -mode cql3 native or -mode cql3 4x.` |
| `-port` | `native=`, `thrift=`, `jmx=` | `native=`. `jmx=` stops with `Port option jmx= was removed. Use -port native=.` and `thrift=` with `Invalid parameter thrift=9160` |
| `-transport` | `factory=` and the SSL options | The SSL options. `factory=` stops at argument parsing |
| `-schema replication(strategy=X)` | Any class on the classpath that extends `AbstractReplicationStrategy` | `NetworkTopologyStrategy` or `EverywhereStrategy`, short or full name. Other names stop with `Invalid replication strategy: X` |
| `-schema compaction(strategy=X)` | Any compaction class that `CFMetaData` loads | The five compaction classes of the vendored tree. Other names stop with `Invalid compaction strategy: X` |
| GC columns and the GC summary lines | Values over JMX, or zero when JMX fails | Removed |
| `-col` | `names=` or `n=`, `slice`, `super=`, `comparator=`, `timestamp=`, `size=` | `names=` or `n=`, `slice`, `timestamp=`, `size=`. Column names are UTF-8, sorted by unsigned byte order. `super=` and `comparator=` stop at argument parsing |
| `CompactionStress`, offline `SchemaInsert` | Write SSTables with server code | Removed |
| `cassandra-stress legacy` | Translates the pre-2.1 command line | Stops at argument parsing: `Command legacy was removed. Run cassandra-stress help to see the commands.` |
| `-graph` without `title=` | Title is null, because the default checks `revision=` | Title is `cassandra-stress - <yyyy-MM-dd HH:mm:ss>`, as the help text states |
| `-send-to` and stressd | The client fails with `NotSerializableException` before it sends the settings | The client sends its command-line arguments as text, and stressd parses them. An older client cannot talk to a new stressd |
| User profiles, other commands, workloads | | Unchanged |

A profile that names a strategy in its own `CREATE KEYSPACE` text passes it to the cluster unchanged.

The user profile flow stays as it is. `StressProfile` creates the keyspace and the table, then reads the table through the `MetadataProvider` that `JavaDriverClient` implements. The generators bind to the driver columns. Only the parse of the keyspace and the table name from the profile CQL moves, from `CQLFragmentParser` to `stress.util.CqlNames`.

### What changes in the code

| Server dependency | Used by | Result |
|---|---|---|
| `thrift.*`, `interface/thrift/gen-java` | Thrift mode | Removed |
| `transport.SimpleClient`, `ResultMessage` | `simplenative` mode | Removed |
| `ColumnFamilyStore`, `StressCQLSSTableWriter`, `CFMetaData`, `QueryProcessor`, `CreateTableStatement` | offline `SchemaInsert`, `CompactionStress` | Removed |
| `tools.NodeProbe` | `JmxCollector`, the GC output of `StressMetrics` | Removed |
| `CQLFragmentParser`, `CqlParser` | `StressProfile` names | `stress.util.CqlNames` |
| `db.marshal.*`, `serializers.*` | generators, `PartitionIterator` | Ported to `stress.marshal`, byte format unchanged |
| `TypeParser` | `-col comparator=` | Removed |
| `AbstractReplicationStrategy`, `CFMetaData.createCompactionStrategy` | `OptionReplication`, `OptionCompaction` | `ReplicationStrategy` and `CompactionStrategy` allow-lists |
| `DatabaseDescriptor.clientInitialization` | `Stress` | Removed |
| `ByteBufferUtil`, `Pair`, `UUIDGen`, `MurmurHash`, `DynamicList`, `LockedDynamicList`, `ConsistencyLevel`, `EncryptionOptions`, `SSLFactory` | many | Trimmed copies in `stress.util` |
| `WindowsTimer`, `FBUtilities`, `NamedThreadFactory`, `FileUtils` | `Stress`, `StressServer`, `SettingsGraph` | Removed, or replaced with JDK classes |
| commons-lang3, commons-cli, json-simple, netty-common | `StressGraph`, `StressMetrics`, `TimestampSerializer`, `Legacy`, `StressServer` | Replaced with JDK classes and the Jackson that `build.xml` declares, so the four jars leave `lib/` |

A file that comes from a Cassandra original starts with `// SPDX-License-Identifier: Apache-2.0`. A unit test compares the serialized bytes of each `stress.marshal` type with fixed bytes that master produces, sets of every element type with a custom comparator included. An end-to-end check writes the column shape of the SCT restore snapshots in `defaults/manager_restore_benchmark_snapshots.yaml` with the released `scylladb/cassandra-stress:3.21.1` image, and the new build reads and validates every row.

### Build and CI

`build.xml` declares the jars that stress code imports, plus the logging and compression jars that the driver loads at run time. Stress uses the Scylla Java driver 4.x only. The resolver brings it with its `native-protocol` and `java-driver-guava-shaded` jars, and no jar is relocated. HdrHistogram moves from 2.1.12 to 2.2.2. Both versions write the same HDR log, format 1.3. `build.xml` pins `jackson-core` to the `jackson-databind` version, because the driver POM declares an older `jackson-core`. `build.xml` declares the dependencies in resolver `<dependencies>` sets, one for run time and one for each test and tool set, and writes no POM.

| Scope | Coordinates |
|---|---|
| runtime | `java-driver-core-shaded` 4.x, commons-math3 3.6.1, snakeyaml, jackson-core, jackson-databind, jctools-core 4.0.7, HdrHistogram 2.2.2, config, slf4j-api 2.x, logback-classic 1.6, `at.yawk.lz4:lz4-java`, snappy-java |
| test | junit-jupiter 6.1.3, junit-platform-launcher 6.1.3, testcontainers-scylladb 2.0.5 (integration tests) |
| build | `maven-resolver-ant-tasks`, `org.jacoco.ant` 0.8.15 (only for `coverage`) |

Every other coordinate goes, `compile-command-annotations` and joda-time included. `<javac>` sets `--release 21` and `-proc:none`. With no annotation processor, the `build` target stops copying `META-INF/hotspot_compiler`, and the `artifacts` target stops excluding it. `conf/jvm-clients.options` keeps only the flags that the drivers need. The integration tests on JDK 21 and 25 decide that list.

No class on the classpath calls `sun.misc.Unsafe`. The driver comes without `jnr-posix`, so it uses the Java clock. The work queues of `StressAction` are the `jctools` atomic queues. `conf/jvm-clients.options` sets `-Dcom.datastax.oss.driver.shaded.netty.noUnsafe=true`, the relocated name of the Netty property in the driver jar. The JDK 25 integration tests run with `--sun-misc-unsafe-memory-access=deny`, so an `Unsafe` call fails CI.

`build.xml` keeps the targets that CI, the Makefile, the Dockerfile and packaging call: `init`, `clean`, `realclean`, `resolver-init`, `resolver-retrieve-build`, `build`, `jar`, `artifacts`, `build-test` and `test`. `test` runs every unit test through `junitlauncher` in one forked JVM, or one class with `-Dtest.name=ClassNameTest`. `coverage` runs the same tests under the JaCoCo agent and writes HTML, XML and CSV reports to `build/coverage`. `integration-test` runs the `*IT` classes in `test/integration` against one Testcontainers ScyllaDB node, and `coverage-all` reports the unit and integration tests together. CI runs `test` and `coverage-all` on JDK 21 and 25 and uploads the report. `artifacts` empties `build/dist` first, so a jar that left the dependency list does not stay in the tarball. `build-project` writes the stress version to the `org/apache/cassandra/stress/stress.version` resource in `build/classes/main`. The `jar` target also writes it to the `Implementation-Version` attribute of the jar manifest. The CI build, unit test and integration test matrices are `["21", "25"]`. The deb and rpm package tests stay on JDK 21, the runtime of the packages. The build workflow passes no `source.version` or `target.version`, so a JDK 25 build still writes Java 21 bytecode.

The server tree, the server tests, the Thrift and ANTLR sources, and the build files that only they use leave the repository. `ide/idea/` goes too, because palantir-java-format now sets the code style.

## Contracts

### Inputs

The command line, after this change. Every other option keeps its form.

```
-mode cql3 native|4x [unprepared] [protocolVersion=N] [compression=none|lz4|snappy] [user= password= ...]
-port native=9042
-transport [truststore= truststore-password= keystore= keystore-password= hostname-verification= ssl-protocol= ssl-alg= store-type= ssl-ciphers=]
-col [names=|n=] [slice] [timestamp=] [size=]
-schema replication(strategy=NetworkTopologyStrategy|EverywhereStrategy ...) compaction(strategy=<one of five> ...)
```

User profiles keep their YAML format. `bin/cassandra-stress` adds the flags of `conf/jvm-clients.options` to the `JVM_OPTS` environment variable, as today.

### Outputs

The interval and summary header loses the five GC fields. The summary loses its five GC lines: `Total GC count`, `Total GC memory`, `Total GC time`, `Avg GC time` and `StdDev GC time`:

```
type, total ops, op/s, pk/s, row/s, mean, med, .95, .99, .999, max, time, stderr, errors
```

`cassandra-stress version` prints these lines, as today. SCT parses them for Argus. The stress version comes from the `stress.version` resource that the build writes, so a source checkout and the jar print the same version. The driver version comes from the `driver.version` key of `com/datastax/oss/driver/Driver.properties` in the driver jar, the file that `Session.OSS_DRIVER_COORDINATES` reads. Both driver lines print it, so the SCT parser finds the key it reads:

```
Version: <version>
scylla-java-driver: <driver version>
scylla-java-driver-4x: <driver version>
```

The distribution keeps `bin/cassandra-stress`, `conf/`, `lib/` and the jar name. The launcher classpath drops `$classes/thrift`.

### Module API

```java
package org.apache.cassandra.stress.marshal;

public abstract class AbstractType<T> {
    public ByteBuffer decompose(T value);
    public T compose(ByteBuffer bytes);
    public String getString(ByteBuffer bytes);
}

package org.apache.cassandra.stress.util;

public final class CqlNames {
    public static String keyspaceOf(String createKeyspaceCql);
    public static String tableOf(String createTableCql);
}

package org.apache.cassandra.stress.settings;

public enum ReplicationStrategy { NetworkTopologyStrategy, EverywhereStrategy; public static String validate(String name); }
public enum CompactionStrategy {
    SizeTieredCompactionStrategy, LeveledCompactionStrategy, TimeWindowCompactionStrategy,
    DateTieredCompactionStrategy, IncrementalCompactionStrategy;
    public static String validate(String name);
}
```

`CqlNames` reads `CREATE KEYSPACE` and `CREATE TABLE` or `CREATE COLUMNFAMILY` in any letter case, with or without `IF NOT EXISTS`, with any run of whitespace between tokens, with a plain or a double-quoted name, and after leading `--`, `//` or `/* */` comments. `tableOf` also reads a qualified `ks.table` name and returns the table part.

`ReplicationStrategy.validate` returns the full `org.apache.cassandra.locator.` name, and `CompactionStrategy.validate` returns the name as given, as today.

## Risks

| Risk | Response |
|---|---|
| A ported serializer changes the bytes, and validation of old data fails | Port the serializer bodies as-is. The `stress.marshal` byte-format test fixes the bytes that master produces for each type. The new build validates data that the 3.21.1 image wrote |
| SCT passes a removed option or strategy, or parses the GC fields | SCT pins `scylladb/cassandra-stress:3.21.1`, so nothing breaks until SCT bumps the image. The bump changes SCT to the new command line and output: it removes `-port jmx=6868` from eight test cases and configurations, changes `SimpleStrategy` to `NetworkTopologyStrategy` in the two Cassandra provision tests, and drops the GC fields from its output parser. A `logback-tools.xml` that names `shaded.com.datastax.oss` loggers changes them to `com.datastax.oss` |
| Without the hand-pinned transitive jars, the resolver picks other versions of Netty or Jackson for the driver | Compare the `build/lib/jars` list against master in the plan. Pin a version only when the integration tests or a CVE require it. Before the SCT bump, one SCT performance run gives the same latency as the current image, and one run each with `use_hdrhistogram: true` and `client_encrypt: true` passes |
| The driver, or the Netty in it, fails on JDK 25 | Add the JVM flags that the JDK 25 integration tests show to be needed, or move to a driver version that runs on JDK 25 |
| The diff is too large to review | Remove files in separate commits (Thrift, simplenative, offline and JMX, server tree, server tests) before the rewrite commits |

## Deferred work

- The Gradle build, in a second pull request under the same key. It moves the source from `src/java` and `test/unit` to `src/main/java` and `src/test/java`.
- A JDK 25 runtime for the Docker image and the packages.
- JDK 27 in CI, after its GA.
- New Java features in stress code. They need a `--release` above 21 and end JDK 21 runtime support.

## Decisions

- Thrift, `simplenative`, offline SSTable writing, `CompactionStress` and JMX go with no replacement. Each one runs on server code, and Cassandra 4.0 removed Thrift. (spec)
- SCT moves to every breaking change of the command line and the output when it bumps the image. This pull request keeps no option or output field for SCT alone. (review)
- JMX goes completely, the GC fields and the GC summary lines included. SCT adapts its output parser when it moves to this release. (review)
- `-col super=` and `comparator=` go, because only Thrift column families used them. `-col slice` stays, because the CQL read uses it. (review)
- `version.properties` and its `createVersionPropFile` target go. `cassandra-stress version` keeps its lines and reads the jar manifest and the drivers. (review)
- `cassandra-stress version` reads the driver `Driver.properties` files as resources and does not call the driver classes, because loading those classes logs an INFO line to stdout ahead of the lines that SCT parses. The stress version comes from a resource that `build-project` writes, because the launcher of a source checkout loads `build/classes/main` before the jar, and that directory has no manifest. (review)
- The replication allow-list leaves out `SimpleStrategy`, `LocalStrategy` and `OldNetworkTopologyStrategy`. Only two SCT provision tests against Cassandra use `SimpleStrategy`, and SCT moves them to `NetworkTopologyStrategy` before the image bump. Only system keyspaces use `LocalStrategy`, and Cassandra 4.0 removed `OldNetworkTopologyStrategy`. (review)
- `-mode` selects driver 4.x with the `4x` token, not `native 4x`, so the removal message and the Inputs contract name `-mode 4x`. (build)
- A ported file starts with an SPDX line, not the ASF block comment, because the repository allows no comments and an SPDX line is a license directive. `NOTICE.txt` keeps the Apache Cassandra attribution. (build)
- `build.xml` writes no POM and uses no maven-ant-tasks. The POMs only fed the resolver, and two resolver `<dependencies>` sets do that without a generated file. (build)
- `SimpleDateSerializer` formats with `java.time` and drops the string parser, because stress only serializes and prints dates, and joda-time left with the server dependencies. (build)
- One `test` target replaces `testold` and `testsome`, which kept the per-test fork and the Cassandra test harness. CI runs `ant test`, so the unit tests run on every pull request. (review)
- No class calls `sun.misc.Unsafe`, because JDK 24 and later warn on each such call and a future JDK removes the methods. Six interleaved runs on a laptop showed no throughput change beyond the run-to-run noise of 15 percent. (review)
- `-transport store-type=` reaches the key stores, so a PKCS12 trust store works. (build)
- One pull request carries the removal, because stress does not compile until the removals and the ports are both in. (spec)
- CI tests on JDK 21 and 25, and this pull request fixes any driver failure on JDK 25. (review)
- JDK 27 joins CI after its GA. (review)
- A unit test fixes the bytes of each ported serializer against master. Data that the 3.21.1 image writes in the shape of the SCT restore snapshots validates with the new build, because the snapshots themselves are terabytes in S3. (build)
- `build.xml` declares HdrHistogram 2.2.2, because stress imports it directly and the 3.x driver declares the range `[2.2,3)`. 2.1.12 and 2.2.2 write byte-identical logs, and each reads the log of the other. (review)
- The `legacy` command goes, because it translated the pre-2.1 Thrift-era command line and it was the only user of commons-cli besides stressd. (review)
- stressd stays. The client sends the argument count and each argument as a UTF-8 string, and stressd parses them with `StressSettings.parse`, because Java deserialization of bytes from any client on port 2159 is a remote code execution risk. The settings classes are no longer `Serializable`. (review)
- The driver 4.x jars come through the resolver without jarjar. The relocation to `shaded.com.datastax` kept the 4.x classes apart from the jars of the server tree, and no jar left on the classpath shares a class path with them. (review)
- `TimestampSerializer` and `TimestampCodec` keep `SimpleDateFormat`, because `java.time` uses the proleptic Gregorian calendar and would print other strings for dates before 1582. Stress writes those strings as CQL literals in unprepared mode. (review)
- `sun.misc.Signal` stays in `Stress`, because a shutdown hook cannot see the signal name and cannot keep the exit codes 130, 134 and 143. (review)
- The unit tests use JUnit 6 through Ant `junitlauncher`, because JUnit 4 is in maintenance and Ant runs the JUnit Platform without a Maven or Gradle build. Ant 1.10.17 ships the `junitlauncher` task. (review)
- `coverage` is its own target and `test` runs without the agent, so a developer run stays fast. CI runs `coverage`. (review)
- Strings in stress code change case with `Locale.ROOT`, because a Turkish default locale turns `serial` into `SERİAL` and the option lookup fails. (review)
- The profile loader rejects a missing `keyspace`, `table` or `queries` with an exception. The other profile checks stay `assert` statements, because the launcher runs without `-ea` and an exception would stop profiles that run today. (review)
- The integration tests start ScyllaDB through Testcontainers and run stress in-process through `Stress.run`, so JaCoCo measures the driver clients, the operations and `StressAction`. The shell scripts in `integration-tests/` stay, because they test the packaged launcher. (review)
- `StressSettings` holds its driver clients and its failure count per instance, and `disconnect()` closes both clients. Static fields made every later run in one JVM, such as each stressd request, reuse the first connection and keyspace. (review)
- The code keeps no Thrift name, the removal messages included, so `-mode thrift` and `-port thrift=` fail as unknown options. (review)
- The runtime leaves out `j2objc-annotations` and `metrics-core`. Guava needs the annotations only at compile time, driver 3.x bundles its own metrics, and driver 4.x uses metrics-core only when stress turns on driver metrics. Both drivers pass the integration tests without them. (review)
- Every workflow sets `permissions: contents: read`, and `build.yml` drops `contents: write`, because it only uploads artifacts. The release workflow keeps its own write permission. (review)
- palantir-java-format formats all Java code, and `ant lint` runs the format check, Error Prone, Checkstyle, PMD and SpotBugs in CI. The IntelliJ settings in `ide/idea/` go, because their Cassandra code style contradicts the formatter. (review)
- Stress uses driver 4.x only, and `-mode cql3 native` and `-mode cql3 4x` both select it. Driver 3.x doubled every client, operation and metadata path, and it kept guava, failureaccess and the 3.x jar on the classpath. (review)
- Driver 4.x reads `-node loadbalance=`: `rr` uses `BasicLoadBalancingPolicy` with every node local, `dc` and `rack` use `DcInferringLoadBalancingPolicy` with the local datacenter, the local rack and `remote-dc=`. `whitelist` ignores every node that is not a contact point. `DcInferringLoadBalancingPolicy` takes the local datacenter from the contact points when `datacenter=` is absent. (review)
- `EpochDayCodec` and `NanoOfDayCodec` bind the generated `date` and `time` values, days since the epoch and nanoseconds of the day, as driver 3.x did. Without them driver 4.x rejects the `Integer` and `Long` values. (review)
- A settings parser throws `InvalidSettingsException`, and only `Stress.main` exits. A bad option sent to stressd stopped the daemon for every client. (review)
- stressd takes `-p <port>`, and `-send-to` takes `host:port`. Contact points and the daemon address go through `HostAndPort`, which reads IPv6 addresses. (review)
- The runtime moves to logback 1.6 and slf4j 2, commons-math3 3.6.1, snakeyaml 2.7, jackson 2.22 and the maintained `at.yawk.lz4` fork of lz4-java. commons-math3 3.6.1 samples the same sequences as 3.2, and its inverse CDFs differ from 3.2 only in the last bit. (review)
- Renovate reads `base.javaDriverVersion` as `java-driver-core-shaded`, and reads every literal `<dependency>` version in `build.xml`. It groups the jackson and the logging updates, because each group must move together. (review)
- `ops(validate=1)` checks clustered tables, collections and `date` columns. The validation path seeds each row from the clustering value that the insert order puts first, sets the full row population on its bounds, and skips the unused lookup of the bind names, which ScyllaDB returns as `(ck)[0]`. `Sets` and `Lists` seed their size per row, and `LocalDates` reads a stored date back as days since the epoch. The insert path keeps its seeds, so scalar columns stay byte-identical. `list` and `set` lengths now follow the row seed. They were random on every run before, so no stored data depends on them. (review)
- A schema statement without schema agreement logs the driver 3.x warning `No schema agreement from live replicas after <n> s. The schema may not be up to date on some nodes.`, because the SCT `SchemaDisagreement` event matches that text and starts its debug collection. Driver 4.x logs a different text. (review)
- A retried operation reuses the statements, the bound query and the expected rows of its first try, because the first try consumes the partition iterators. A retried insert wrote nothing and counted as a success. (review)
- Validation orders clustering values by their stored CQL order, reversed for `DESC` columns, and seeds each row from the value that the insert order puts first. Java order differs from stored order for `blob`, `timeuuid`, `uuid` and `inet`. The insert and read paths keep their order, and `GeneratedDataCompatibilityTest` pins their rows. (review)
- A `-send-to` run exits with 1 unless the daemon ends with `END`, and the daemon answers `FAILURE` for any parse error. The profile `insert:` block reads `consistencyLevel` and `serialConsistencyLevel` in any letter case. A table whose value columns are all unsupported gets a key-only `INSERT`. (review)
- `PreviousReleaseIT` writes with the `scylladb/cassandra-stress:3.21.1` image and validates with the new build, predefined rows and two profiles with `blob`, `timeuuid`, `inet` and `date` clustering columns in mixed and all-`DESC` order. The profiles keep two clustering columns, because the insert of a table with three or more writes only the first rows of each partition in 3.21.1. The profile leaves out collections, because 3.21.1 sized them from an unseeded distribution. (review)
- A validation slice compares the clustering tuple by value, as CQL does, so a `DESC` prefix uses `<=`/`<` for its start and `>=`/`>` for its end. Slices stop at the first clustering column whose order differs from the first one, because a value range over a mixed `ASC`/`DESC` prefix is not one contiguous run of rows. The full-partition query still validates every row of such a table. (review)
- The insert of a table with three or more clustering columns writes every row of the partition. It stopped when a middle clustering level passed the same level of the last row, without comparing the levels above it. The rows that 3.21.1 wrote come first and stay byte-identical, and `GeneratedDataCompatibilityTest` pins them. (review)
