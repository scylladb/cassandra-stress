# QATOOLS-123 — implementation plan

**Spec:** `tasks/QATOOLS-123/spec.md`

A decision during the build that changes what the spec states updates the
spec in the same commit. A line in its `Decisions` section records the
decision when the Design section does not state the reason. This paragraph
stays in every plan built from a spec.

## Rules

- Paths below are relative to the repository root. `S` is `src/java/org/apache/cassandra/stress`. `T` is `test/unit/org/apache/cassandra/stress`.
- Each task ends with a green build. Tasks 1 to 10 keep the server tree in place, so the build compiles at every commit. Task 11 deletes the tree only after no stress file imports it.
- Verify sequence, from `CLAUDE.md`: `ant build-test`, then `ant test`. Tasks 1 to 13 ran the earlier `testold -Dtest.name='stress/**/*Test'` form. Run `ant clean` first after a task that deletes a class, because Ant compiles only the changed sources and a stale class file hides a broken reference.
- Follow `docs/standards/`: Allman braces and four spaces, the four import groups, JUnit 4 with `org.junit.Assert`, the test for `S/<pkg>/<Class>.java` in `T/<pkg>/<Class>Test.java`.
- A file copied from a Cassandra original starts with `// SPDX-License-Identifier: Apache-2.0` and carries no other comment.
- Commit subjects: `type(scope): QATOOLS-123 <subject>`, with `!` on a commit that removes a user-facing option or output field.
- Write no comments in code. The no-comments hooks block them.
- Before Task 1, record the master baseline: `ls build/lib/jars > /tmp/qatools-123-jars-master.txt` after `ant build-test` on master.

## Task 1 — Remove the Thrift mode

**Files:**
- Delete: `S/util/ThriftClient.java`, `S/util/SimpleThriftClient.java`, `S/util/SmartThriftClient.java`, `S/operations/predefined/ThriftInserter.java`, `S/operations/predefined/ThriftReader.java`, `S/operations/predefined/ThriftCounterAdder.java`, `S/operations/predefined/ThriftCounterGetter.java`
- Modify: `S/settings/SettingsMode.java:64,155,202` (the `thrift` option groups), `S/settings/StressSettings.java:36-44,97-130` (the Thrift client getters), `S/settings/SettingsSchema.java:33,71-76,237-300` (`createKeySpacesThrift`), `S/settings/SettingsPort.java:50` (`thrift=`), `S/settings/SettingsTransport.java:41-77,133` (`ITransportFactory`, `factory=`), `S/settings/Legacy.java`, `S/settings/CliOption.java`, `S/settings/SettingsCommandPreDefined.java`, `S/operations/predefined/PredefinedOperation.java`, `S/operations/predefined/CqlOperation.java:356-360,379-383,505-545` (`Cql3CassandraClientWrapper`), `S/operations/userdefined/SchemaQuery.java`, `S/operations/userdefined/ValidatingSchemaQuery.java`, `S/operations/userdefined/SchemaStatement.java`, `S/operations/userdefined/TokenRangeQuery.java`, `S/StressProfile.java:69,467,706` (`thriftInsertId`, `Compression.NONE`), `S/Operation.java`, `S/StressAction.java`, `S/generate/PartitionIterator.java`, `bin/cassandra-stress:74` (`$classes/thrift`)
- Test: `T/settings/SettingsModeTest.java`

**Internals:** `SettingsMode` throws `IllegalArgumentException("Mode thrift was removed. Use -mode cql3 native or -mode cql3 4x.")` when the `-mode` arguments hold `thrift`. `SettingsTransport` keeps the SSL options and builds no transport factory.

- [x] Write `SettingsModeTest`: `-mode thrift` and `-mode thrift smart` throw with the message above. `-mode cql3 native` and `-mode cql3 4x` parse.
- [x] Run it and confirm the failure.
- [x] Delete the Thrift classes and remove every Thrift branch from the files above. Remove `-port thrift=` and `-transport factory=`.
- [x] Run `grep -rn "org.apache.cassandra.thrift\|org.apache.thrift" S` and confirm no match.
- [x] Run the verify sequence.
- [x] Commit `feat(mode)!: QATOOLS-123 remove the thrift mode`, with the boxes of this task checked.

## Task 2 — Remove the simplenative mode

**Files:**
- Modify: `S/settings/SettingsMode.java:188-200` (the `simplenative` group), `S/settings/StressSettings.java:42` (`SimpleClient`), `S/StressAction.java`, `S/Operation.java`, `S/operations/predefined/CqlOperation.java:45,362-366,394-396,472-503` (`SimpleClientWrapper`, `ResultMessage`)
- Test: `T/settings/SettingsModeTest.java`

- [x] Add a case to `SettingsModeTest`: `-mode cql3 simplenative` throws `Mode simplenative was removed. Use -mode cql3 native or -mode cql3 4x.`
- [x] Run it and confirm the failure.
- [x] Remove the `simplenative` group, the `SimpleClient` getter and every `SimpleClient` path.
- [x] Run `grep -rn "transport.SimpleClient\|ResultMessage" S` and confirm no match.
- [x] Run the verify sequence.
- [x] Commit `feat(mode)!: QATOOLS-123 remove the simplenative mode`, with the boxes of this task checked.

## Task 3 — Remove offline SSTable writing and CompactionStress

**Files:**
- Delete: `S/CompactionStress.java`, `src/java/org/apache/cassandra/io/sstable/StressCQLSSTableWriter.java`, `test/unit/org/apache/cassandra/tools/CompactionStressTest.java`
- Modify: `S/operations/userdefined/SchemaInsert.java` (keep the online insert path only), `S/StressProfile.java:497-570` (`getOfflineGenerator`, `getCreateStatement`, `getOfflineInsert`), `bin/` and `build.xml` entries that name `CompactionStress`

- [x] Run `grep -rn "CompactionStress\|getOfflineInsert\|getOfflineGenerator\|getCreateStatement\|StressCQLSSTableWriter" S bin build.xml` and record the hits.
- [x] Delete the two files and the three `StressProfile` methods. Remove the offline branch of `SchemaInsert`.
- [x] Run the grep again and confirm no match.
- [x] Run the verify sequence. `StressProfileTest` must pass, because it covers the online profile path.
- [x] Commit `feat!: QATOOLS-123 remove offline sstable writing and CompactionStress`, with the boxes of this task checked.

## Task 4 — Remove JMX and the GC output

**Files:**
- Delete: `S/util/JmxCollector.java`
- Modify: `S/report/StressMetrics.java:49,61-66,104-119,228-237,251-257,345,354-374,397-399` (the `GcStats` fields, the GC columns of `HEADMETRICS`, `printRow`, the five GC summary lines), `S/settings/SettingsPort.java:51` (`jmx=`)
- Test: `T/report/StressMetricsTest.java`

**Internals:** `StressMetrics.HEADMETRICS` ends at `"errors"`. `printRow` drops its `GcStats` parameter.

- [x] Write `StressMetricsTest`: `HEADMETRICS` equals the fourteen fields of the spec Outputs contract, in order.
- [x] Run it and confirm the failure.
- [x] Delete `JmxCollector`, the GC fields, the GC columns, the GC summary lines, `SettingsNode.resolveAllPermitted` and `-port jmx=`.
- [x] Run `grep -rn "GcStats\|JmxCollector\|NodeProbe\|jmxPort" S` and confirm no match.
- [x] Run the verify sequence.
- [x] Commit `feat(report)!: QATOOLS-123 remove jmx and the gc output`, with the boxes of this task checked.

## Task 5 — Remove `-col super=` and `-col comparator=`

**Files:**
- Modify: `S/settings/SettingsColumn.java:46-140,151-176`
- Test: `T/settings/SettingsColumnTest.java`

**Internals:** `SettingsColumn` builds the column names as UTF-8 bytes, sorted by unsigned byte order. The `names` and `namestrs` fields stay.

- [x] Write `SettingsColumnTest`: `-col n=FIXED(3)` gives the names `C0`, `C1`, `C2`. `-col names=b,a` gives `a`, `b`. `-col super=1` and `-col comparator=UTF8Type` throw `IllegalArgumentException`.
- [x] Run it and confirm the failure of the two removed options.
- [x] Remove the two options and the `TypeParser`, `AbstractType` and `BytesType` uses from `SettingsColumn`.
- [x] Run the verify sequence.
- [x] Commit `feat(settings)!: QATOOLS-123 remove the super and comparator column options`, with the boxes of this task checked.

## Task 6 — Port the marshal types to `stress.marshal`

**Files:**
- Create: `S/util/ByteBufferUtil.java` and `S/util/UUIDGen.java` with the members that the serializers call, `S/marshal/AbstractType.java`, `S/marshal/TypeSerializer.java`, `S/marshal/MarshalException.java`, one class per type that `S/generate/values/*` and `S/generate/PartitionIterator.java` import: `AsciiType`, `UTF8Type`, `BytesType`, `BooleanType`, `ByteType`, `ShortType`, `Int32Type`, `LongType`, `FloatType`, `DoubleType`, `DecimalType`, `IntegerType`, `InetAddressType`, `UUIDType`, `TimeUUIDType`, `DateType`, `SimpleDateType`, `TimeType`, `ListType`, `SetType`, with their serializers from `src/java/org/apache/cassandra/serializers/`
- Modify: every file under `S/generate/values/`, `S/generate/PartitionIterator.java`
- Test: `T/marshal/AbstractTypeTest.java`, fixtures in `test/resources/stress/marshal/`

**Internals:** `AbstractType<T>` implements `Comparator<ByteBuffer>` with the comparator of each Cassandra type, because `SetSerializer` sorts the set elements with it before it writes them. It holds `decompose`, `compose`, `getString` and `getSerializer`. The fixtures hold sets of every element type with a custom comparator, in an order other than the sort order. `ListType.getInstance(AbstractType, boolean)` and `SetType.getInstance(AbstractType, boolean)` keep their signatures. Each serializer body is a copy of the Cassandra original.

- [x] Generate the master fixture: on master, write a throwaway main that runs `decompose` on fixed values for each type above through the server classes, and store the hex of each result in `test/resources/stress/marshal/master.txt`. Keep the main out of the commit.
- [x] Check old data: write the column shape of one SCT restore snapshot from `defaults/manager_restore_benchmark_snapshots.yaml` with the released `scylladb/cassandra-stress:3.21.1` image, then read and validate every row with the new build on JDK 21 and 25 and both drivers. A read with another column size is the negative control. The snapshots themselves are terabytes in S3.
- [x] Write `AbstractTypeTest`: for each fixture line, `decompose` of the value equals the stored bytes, and `compose` of the bytes equals the value.
- [x] Run it and confirm the failure: `stress.marshal` does not exist.
- [x] Create the classes, then switch the imports in `S/generate` from `org.apache.cassandra.db.marshal` to `org.apache.cassandra.stress.marshal`.
- [x] Run `grep -rn "org.apache.cassandra.db.marshal\|org.apache.cassandra.serializers" S` and confirm no match.
- [x] Run the verify sequence.
- [x] Commit `refactor(marshal): QATOOLS-123 port the marshal types into stress`, with the boxes of this task checked.

## Task 7 — Port the utility helpers to `stress.util`

**Files:**
- Create: `S/util/Pair.java`, `S/util/MurmurHash.java`, `S/util/DynamicList.java`, `S/util/LockedDynamicList.java`, `S/util/ConsistencyLevel.java`, `S/util/EncryptionOptions.java`, `S/util/SSLFactory.java`. Each one keeps only the members that stress calls.
- Modify: `S/settings/SettingsGraph.java` (`File.createTempFile` replaces `FileUtils.createTempFile`)
- Modify: the importers that `grep -rln "org.apache.cassandra.utils\|org.apache.cassandra.db.ConsistencyLevel\|org.apache.cassandra.config.EncryptionOptions\|org.apache.cassandra.security\|org.apache.cassandra.io.util.FileUtils\|org.apache.cassandra.concurrent" S` lists, `S/Stress.java:27-31,65-73` (`DatabaseDescriptor.clientInitialization`, `WindowsTimer`), `S/StressServer.java` (`NamedThreadFactory`)

**Internals:** `Stress` drops the Windows timer calls and `DatabaseDescriptor.clientInitialization`. `StressServer` uses `Executors.newCachedThreadPool` with a thread factory that sets the name. `ConsistencyLevel` keeps the constants and the driver conversion that stress uses.

- [x] Run the existing tests: `DistributionSequenceTest` covers `DynamicList` through `Seed`, and `SettingsNodeTest` covers the settings. They pass before the change.
- [x] Create the trimmed copies and switch every import.
- [x] Run the grep above and confirm no match.
- [x] Run the verify sequence.
- [x] Commit `refactor(util): QATOOLS-123 port the utility helpers into stress`, with the boxes of this task checked.

## Task 8 — Read the profile names with `CqlNames`

**Files:**
- Create: `S/util/CqlNames.java`
- Modify: `S/StressProfile.java:43-46,152-200` (`CQLFragmentParser`, `CqlParser`, `RequestValidationException`, `SyntaxException`)
- Test: `T/util/CqlNamesTest.java`

**Internals:** `keyspaceOf` and `tableOf` match one regular expression each, case-insensitive: `CREATE KEYSPACE (IF NOT EXISTS)? <name>` and `CREATE TABLE (IF NOT EXISTS)? (<name>\.)?<name>`, where `<name>` is `"[^"]+"` or `\w+`, with `\s+` between tokens. A quoted name keeps its case. An unquoted name is lower-cased. No match throws `IllegalArgumentException` with the CQL text.

- [x] Write `CqlNamesTest` with these cases: plain, `IF NOT EXISTS`, mixed letter case, two spaces and a newline between tokens, a quoted name, a qualified `ks.table`, and a statement that matches nothing.
- [x] Run it and confirm the failure.
- [x] Create `CqlNames` and switch `StressProfile` to it.
- [x] Run the verify sequence. `StressProfileTest` must pass.
- [x] Commit `refactor(profile): QATOOLS-123 read profile names without the cql parser`, with the boxes of this task checked.

## Task 9 — Check the strategies against allow-lists

**Files:**
- Create: `S/settings/ReplicationStrategy.java`, `S/settings/CompactionStrategy.java`
- Modify: `S/settings/OptionReplication.java:30,71-94`, `S/settings/OptionCompaction.java:63-77`
- Test: `T/settings/ReplicationStrategyTest.java`, `T/settings/CompactionStrategyTest.java`

**Internals:** `ReplicationStrategy.validate` accepts the short name or `org.apache.cassandra.locator.<name>` and returns the full name. `CompactionStrategy.validate` accepts the short name or `org.apache.cassandra.db.compaction.<name>` and returns the name as given. Each throws `IllegalArgumentException("Invalid replication strategy: " + name)` or `"Invalid compaction strategy: " + name`.

- [x] Write the two tests: each allowed name, short and full. `SimpleStrategy`, `LocalStrategy`, `OldNetworkTopologyStrategy` and `java.lang.String` fail with the message.
- [x] Run them and confirm the failure.
- [x] Create the enums and switch the two adapters to them.
- [x] Run the verify sequence.
- [x] Commit `feat(schema)!: QATOOLS-123 check strategies against fixed lists`, with the boxes of this task checked.

## Task 10 — Read the versions from the manifest and the drivers

**Files:**
- Modify: `.gitignore` (the `src/resources/org/apache/cassandra/config/` entry), `S/settings/SettingsMisc.java:128-168` (`maybePrintVersion`, `parseVersionFile`), `build.xml:1026-1034` (`createVersionPropFile`), `build.xml:1113` (its `antcall`), `build.xml:1138-1140` (the `jar` manifest), `build.xml:1204` (the exclude), `build.xml:91,387` (`version.properties.dir`)
- Test: `T/settings/SettingsMiscTest.java`

**Internals:** `SettingsMisc.versionLines(String stressVersion, String driver3Version, String driver4Version)` returns the three lines of the spec Outputs contract. `maybePrintVersion` passes `stressVersion()`, which reads the `org/apache/cassandra/stress/stress.version` resource that `build-project` writes, and the `driver.version` key of each driver's `Driver.properties` resource, read by `driver3Version()` and `driver4Version()`. The `jar` target adds `<manifest><attribute name="Implementation-Version" value="${version}"/></manifest>`.

- [x] Write `SettingsMiscTest`: `versionLines("1.0.0", "3.11.5.18", "4.19.2.1")` equals the three lines.
- [x] Run it and confirm the failure.
- [x] Add `versionLines`, switch `maybePrintVersion`, remove `parseVersionFile`, `createVersionPropFile` and its property, and add the manifest attribute.
- [x] Run `ant artifacts`, extract `build/cassandra-stress-bin.tar.gz`, and run its `bin/cassandra-stress version`. Confirm the three lines with real versions.
- [x] Run the verify sequence.
- [x] Commit `refactor(version): QATOOLS-123 read versions from the manifest and the drivers`, with the boxes of this task checked.

## Task 11 — Delete the server tree and the server tests

**Files:**
- Delete: every directory under `src/java/org/apache/cassandra/` except `stress/`, `src/java/com/datastax/`, `src/antlr/`, `src/gen-java/`, `interface/`, `src/resources/org/apache/cassandra/cql3/`, `test/distributed`, `test/long`, `test/burn`, `test/microbench`, `test/data`, `test/conf`, `test/resources` except the files that Task 6 adds, every directory under `test/unit/org/apache/cassandra/` except `stress/`, `.build/dependency-check-suppressions.xml`, `eclipse_compiler.properties`, `ide/idea-iml-file.xml`, `NEWS.txt`, `README-cassandra.asc`
- Keep: `src/resources/org/apache/cassandra/stress/graph/graph.html`, `ide/idea/`
- Modify: `build.xml` `build_java` (the `gen-java` and Thrift source paths), `build-project` (the `gen-cql3-grammar` dependency and the `hotspot_compiler` copy), `cassandra-stress.classpath` (the Thrift classes), and `testmacrohelper` (Ant `<junit>` with the `xml` and `brief` formatters, because `JStackJUnitTask` and the Cassandra formatters lived in `test/unit/org/apache/cassandra/`)

- [x] Run `grep -rnE "import (static )?org\.apache\.cassandra\.[a-z]+" S T | grep -v "org.apache.cassandra.stress"` and confirm no match. Stop and go back to the task that owns a hit.
- [x] Delete the paths above with `git rm -r`.
- [x] Make the `build.xml` changes above, so `build-test`, `testold`, `testsome` and `artifacts` run on the stress tree only.
- [x] Run the verify sequence. `build.xml` still declares the server jars, so only the source set changes here.
- [x] Commit `chore!: QATOOLS-123 delete the vendored cassandra source`, with the boxes of this task checked.

## Task 12 — Trim `build.xml` to the stress dependencies and targets

**Files:**
- Modify: `bin/cassandra-stress:83-86,148` (`CONFIG_FILE_REALPATH` and `-Dcassandra.config`, which only `DatabaseDescriptor` read), `build.xml:550-984` (`maven-declare-dependencies`), `build.xml:169-170` (`java11-jvmargs`), `build.xml:176-181` (`build.classes.thrift`), `build.xml:1088-1110` (`build_java`), `build.xml:1120-1131` (the POMs), `conf/jvm-clients.options`, `.github/workflows/test.yml:18,30,43,55,106`
- Delete targets: `check-gen-cql3-grammar`, `gen-cql3-grammar`, `maven-ant-tasks-*`, `maven-ant-tasks-retrieve-build`, `echo-base-version` when nothing calls it, `check-gen-thrift-java`, `gen-thrift-java`, `gen-thrift-py`, `test-run`, `test-cdc`, `msg-ser-*`, `cql-test`, `cql-test-some`, `test-jvm-dtest`, `test-jvm-upgrade-dtest`, `mvn-install`, `generate-idea-files`

**Internals:** one POM `cassandra-stress` with the coordinates of the spec table, HdrHistogram at 2.2.2. `<javac>` sets `release="21"` and `<compilerarg value="-proc:none"/>`, and takes no `--add-exports` or `--add-opens`. `artifacts` loses its `hotspot_compiler` exclude.

- [x] Run `grep -rn "ant \|make " .github Makefile Dockerfile scripts dist` and list every target that they call. Each one must stay.
- [x] Rewrite `maven-declare-dependencies` to the spec table and remove the other coordinates and the `parent`, `all` and `thrift` POMs.
- [x] Remove the targets above and the properties that only they read.
- [x] Remove from `conf/jvm-clients.options` the flags for JDK internals that only server code used: `jdk.internal.misc`, `jdk.internal.module`, `jdk.internal.ref`, the `java.rmi` and `java.management.rmi` exports, `com.sun.management.internal`, `-Djdk.attach.allowAttachSelf`.
- [x] Set every `java:` matrix in `.github/workflows/test.yml` to `["21", "25"]`.
- [x] Run `ant realclean`, then the verify sequence on JDK 21.
- [x] Run `ls build/lib/jars` and diff it against the master baseline. Record each version change of Netty, Guava and Jackson in the pull request body.
- [x] Build the Docker image with the `docker build` command of `make docker-build`, under a local tag, and run its `version` command.
- [x] Build the deb in `ubuntu:24.04` and the rpm in `fedora:42`, install each with `scripts/install-deb.sh` and `scripts/install-rpm.sh`, and pass `scripts/check-version.sh`.
- [x] Commit `build!: QATOOLS-123 keep only the stress dependencies and targets`, with the boxes of this task checked.

## Task 13 — Run on JDK 25

**Files:**
- Modify: `conf/jvm-clients.options` when a driver needs a flag, `build.xml` driver versions when a flag does not suffice

- [x] Run the verify sequence with `JAVA_HOME` set to Temurin 25.
- [x] Start Scylla 2025.1 in Docker with port 9042 published, then run each script in `integration-tests/` with the 3.x and the 4.x driver on JDK 21 and on JDK 25.
- [x] For each failure on JDK 25, add the smallest flag to `conf/jvm-clients.options`, or move to a driver version that runs on JDK 25. Run the step again.
- [x] Run a write with `-log hdrfile=` on JDK 25, and read the log with the HdrHistogram 2.1.12 reader.
- [x] Run a write with `-transport truststore=` on JDK 25 against a Scylla with client encryption, with a JKS and a PKCS12 trust store and both drivers.
- [x] Run the verify sequence on JDK 21 again.
- [x] Commit `fix: QATOOLS-123 run the drivers on JDK 25`, with the boxes of this task checked. Skip the commit when no file changed, and check this box.

## Task 14 — Close the spec checks

- [x] Confirm the spec Contracts against the jar: `bin/cassandra-stress help` lists no removed option, `-mode thrift` and `-mode cql3 simplenative` print the removal message, the interval header ends at `errors`, and `version` prints three lines.
- [x] Update the spec in the same commit when the build changed a design point, with a `(build)` line in `Decisions`.
- [x] Update the pull request body: the removed options, the jar changes from Task 12, and `refs QATOOLS-123` as the last line, because the Gradle pull request under the same key finishes the task.
- [x] Commit the checked boxes of this plan.

## Task 15 — Close the second review

**Files:**
- Modify: `README.md` (`-mode`), `conf/logback.xml` (the `com.thinkaurelius.thrift` logger), `S/settings/SettingsMode.java`, `S/settings/SettingsPort.java`, `S/util/CqlNames.java`, `S/settings/SettingsMisc.java`, `build.xml` (`build-project`, the test classpath)
- Test: `T/settings/SettingsModeTest.java`, `T/settings/SettingsPortTest.java`, `T/util/CqlNamesTest.java`, `T/settings/SettingsMiscTest.java`

**Internals:** `SettingsPort.get` throws `IllegalArgumentException("Port option jmx= was removed. Use -port native=.")` for `jmx=` and `thrift=`. The `CqlNames` patterns skip leading whitespace, `--` and `//` line comments, and `/* */` block comments. The test classpath leaves out `build/lib/cassandra-stress.jar`, so an old jar does not hide the compiled classes.

- [x] Add the cases to the four tests and run them.
- [x] Make the changes above.
- [x] Run `bin/cassandra-stress version` from the source checkout and confirm the real version.
- [x] Run the verify sequence.

## Task 16 — Remove the old libraries, `legacy` and the jarjar step

**Files:**
- Delete: `S/settings/Legacy.java`, `S/settings/LoadBalanceStrategyProvidable.java`, `jarjar.rules`
- Create: `S/util/Sleep.java`
- Modify: `build.xml` (runtime set, the jarjar targets, `artifacts`), `conf/jvm-clients.options`, `conf/logback.xml`, `README.md`, `S/StressGraph.java`, `S/StressServer.java`, `S/settings/SettingsGraph.java`, the files that import `shaded.com.datastax`
- Test: `T/StressGraphTest.java`, `T/StressServerTest.java`, `T/settings/SettingsGraphTest.java`, `T/settings/StressSettingsTest.java`, `T/settings/LoadBalanceTypeTest.java`, `T/report/StressMetricsTest.java`

**Internals:** `StressGraph` builds its JSON with Jackson and inserts it with `Matcher.quoteReplacement`. `StressServer` parses `-h`, `--host` and `--host=` and sets `SETTINGS_FILTER` on its stream. `AuthProvider`, `ProtocolVersion`, `StressYaml.QueryDef` and `StressYaml.TokenRangeQueryDef` implement `Serializable`. `SettingsGraph` selects the default title on `title=` and formats it with `uuuu-MM-dd HH:mm:ss`.

- [x] Write the tests above and run them.
- [x] Replace commons-lang3, commons-cli, json-simple, the Guava calls and the Netty thread-locals with JDK classes and Jackson.
- [x] Remove `legacy`, netty-common, and the jarjar targets. Resolve `java-driver-core-shaded` through the resolver.
- [x] Remove the comments and the ASF block headers. Compile with `-g:none` before and after, and confirm identical class files.
- [x] Confirm that `build/lib/jars` lists the same jars as before the jarjar change.
- [x] Run the verify sequence, and the integration scripts with both drivers on JDK 21 and on JDK 25 with `--sun-misc-unsafe-memory-access=deny`.

## Task 17 — Java 21 APIs, JUnit 6 and coverage

**Files:**
- Modify: `build.xml` (`test-deps`, `coverage-deps`, `run-unit-tests`, `test`, `coverage-init`, `coverage`), `.github/workflows/test.yml`, `docs/standards/testing/test-writing.md`, `docs/INDEX.md`, `CLAUDE.md`, and the stress files that the internals name
- Test: every class under `T/`, rewritten for Jupiter, plus `T/OperationTest.java`, `T/util/ByteBufferUtilTest.java`, `T/util/SleepTest.java`

**Internals:** `toUpperCase` and `toLowerCase` take `Locale.ROOT`. `ByteBufferUtil.compareUnsigned` uses `ByteBuffer.mismatch`. `Operation.hexPreview` uses `HexFormat`. `Stream.toList`, `List.getFirst`, `Math.clamp`, `String.isBlank`, `Objects.requireNonNullElse`, `Thread.ofPlatform` and `removeIf` replace the older forms. `PredefinedOperation.ColumnSelection` is a record. `StressProfile` throws `IllegalArgumentException` for a missing `keyspace`, `table` or `queries`.

- [x] Apply the changes above, without virtual threads.
- [x] Rewrite each test for JUnit Jupiter: package-private classes, `assertThrows`, parameterized tests, `@TempDir`.
- [x] Add the `coverage` target, and run it on JDK 21 and 25.
- [x] Run the verify sequence, and the integration scripts with both drivers on JDK 21 and on JDK 25 with `--sun-misc-unsafe-memory-access=deny`.

## Task 18 — Coverage and Testcontainers integration tests

**Files:**
- Create: `test/integration/org/apache/cassandra/stress/{ScyllaNode,CassandraStress,StressResult,PredefinedCommandsIT,UserProfileIT,OutputsIT}.java`, unit tests for `report`, `generate`, `marshal`, `util`, `util/codecs` and `settings`
- Modify: `build.xml` (`integration-deps`, `build-integration-test`, `integration-test`, `coverage-all`), `.github/workflows/test.yml`, `S/settings/StressSettings.java`, `S/util/JavaDriverClient.java`, `S/util/JavaDriverV4Client.java`, `S/Stress.java`, `S/settings/Command.java`, `S/report/TimingIntervals.java`, `S/marshal/TimeSerializer.java`

**Internals:** `ScyllaNode` starts one `ScyllaDBContainer` per JVM. `CassandraStress` calls `Stress.run` with `-node`, `-port`, `-mode` and `-log file=`, and returns a `StressResult`. The driver clients, their failure count and the prepared-statement caches are per instance. `help version` prints the description. The unused `TimingIntervals` bounds and `TimeSerializer` parser go.

- [x] Write the unit tests and the integration tests.
- [x] Fix what they found: the static driver clients, the static statement caches, `help version`, the unused code.
- [x] Run `ant test`, `ant integration-test` and `ant coverage-all`.

## Task 19 — Close the CodeQL alerts and trim the runtime

**Files:**
- Modify: `S/StressServer.java`, `S/Stress.java`, the settings and generator classes that implemented `Serializable`, `S/generate/Distribution.java`, `S/settings/SettingsMode.java`, `S/settings/SettingsPort.java`, `build.xml`, `.github/workflows/{test,build,dockerhub-description}.yml`, `NOTICE.txt`, `docs/standards/global/conventions.md`
- Test: `T/StressServerTest.java`, `T/settings/SettingsModeTest.java`, `T/settings/SettingsPortTest.java`

**Internals:** `StressServer.writeCommand` writes the argument count and each argument with `writeUTF`. `StressServer.readCommand` rejects a count below 0 or above 1024. `Distribution.average` steps with `d = (float) (d + 0.02d)`. The Thrift names leave the removed-option guards.

- [x] Replace Java serialization in stressd with the argument protocol, and remove `Serializable`, `transient` and the `SettingsColumn` hooks.
- [x] Set `contents: read` on the workflows and the explicit cast in `Distribution`.
- [x] Exclude `j2objc-annotations` and `metrics-core`, and remove the remaining Thrift references.
- [x] Run `ant test` and `ant integration-test`.

## Task 20 — Driver 4.x only, settings errors and runtime upgrades

**Files:**
- Create: `S/settings/InvalidSettingsException.java`, `S/util/HostAndPort.java`, `S/core/CqlTypes.java`, `S/util/codecs/{EpochDayCodec,NanoOfDayCodec}.java`, `S/ProfileGenerators.java`, `S/ProfileInsert.java`, `T/integration/.../DriverOptionsIT.java`
- Delete: `S/core/{BatchStatementType,BoundStatement,ColumnDefinitions,ColumnMetadata,DataType,TableMetadata}.java`, `S/settings/ConnectionAPI.java`, `S/util/JavaDriverV4{ConfigBuilder,SessionBuilder}.java`
- Modify: `S/util/JavaDriverClient.java`, `S/StressProfile.java`, `S/StressServer.java`, `S/Stress.java`, the `Settings*` parsers, the user-defined and predefined operations, `build.xml`, `conf/logback.xml`, `conf/jvm-clients.options`, `README.md`, `renovate.json`

**Internals:** `JavaDriverClient` is the driver 4.x client. `StressProfile` keeps the YAML and the lazy state, `ProfileInsert` builds the insert CQL and its options, and `ProfileGenerators` builds the column generators. `StressSettings.output()` carries the result logger, so stressd sends the connection and schema messages to its client.

- [x] Replace `System.exit` in the parsers with `InvalidSettingsException`, and test stressd with an invalid command.
- [x] Close the stressd sockets with try-with-resources, and add `-p` and `host:port`.
- [x] Upgrade logback, slf4j, commons-math3, snakeyaml, jackson and lz4-java.
- [x] Remove driver 3.x, guava and the driver override targets, and port load balancing, whitelist, token ranges and the date and time binding to driver 4.x.
- [x] Split `StressProfile`, and convert the fall-through switches.
- [x] Check every SCT command, profile, output parser and log event against `master`, and keep the SCT schema agreement event.
- [x] Rerun the old-data check with driver 4.x on JDK 21 and 25: data that the 3.21.1 image wrote validates, and a read with another column size fails every row.
- [x] Fix the retry, validation order, `-send-to` exit code, profile consistency keys, key-only insert, empty-page and overload findings, each with a test that fails without the fix.
- [x] Pin the generated rows in `GeneratedDataCompatibilityTest`, and validate data that the 3.21.1 image wrote in `PreviousReleaseIT`.
- [x] Write every row of a partition with three or more clustering columns, with `PartitionWriteTest` failing first.
- [x] Make `ops(validate=1)` read clustered rows, collections and dates back as the insert wrote them, with a failing test first for each bug.
- [x] Run `ant lint`, `ant test`, `ant integration-test` and `ant coverage-all`.

## Task 21 — Close the output files

**Files:**
- Create: `S/report/HdrLog.java`, `T/StressTest.java`, `T/report/HdrLogTest.java`
- Modify: `S/util/MultiResultLogger.java`, `S/Stress.java`, `S/StressServer.java`, `S/StressAction.java`, `S/report/StressMetrics.java`, `S/settings/SettingsLog.java`, `S/settings/SettingsGraph.java`, `T/util/MultiResultLoggerTest.java`, `T/StressServerTest.java`

**Internals:** `MultiResultLogger.addOwnedStream` takes ownership, and `close` flushes every stream and closes the owned ones. `Stress.run(StressSettings, String[])` opens the logger with try-with-resources and deletes the graph log in its `finally`. `StressAction.run` opens one `HdrLog` and passes it to each non-warmup `StressMetrics`.

- [x] Write the failing tests: concurrent close, owned and borrowed streams, the failure in the log file, the graph log deletion and the stressd cancel join.
- [x] Make `MultiResultLogger` closeable with copy-on-write lists and explicit ownership.
- [x] Share one `HdrLog` across the steps of a run, and close it.
- [x] Delete the graph log, skip it for `-send-to`, and join the stressd action thread on cancel.
- [x] Run `ant lint`, `ant test` and `ant integration-test`.
