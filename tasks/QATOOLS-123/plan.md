# QATOOLS-123 — implementation plan

**Spec:** `tasks/QATOOLS-123/spec.md`

A decision during the build that changes what the spec states updates the
spec in the same commit. A line in its `Decisions` section records the
decision when the Design section does not state the reason. This paragraph
stays in every plan built from a spec.

## Rules

- Paths below are relative to the repository root. `S` is `src/java/org/apache/cassandra/stress`. `T` is `test/unit/org/apache/cassandra/stress`.
- Each task ends with a green build. Tasks 1 to 10 keep the server tree in place, so the build compiles at every commit. Task 11 deletes the tree only after no stress file imports it.
- Verify sequence, from `CLAUDE.md`: `ant build-test`, then `ant testold -Dtest.name='stress/**/*Test'`. Run `ant clean` first after a task that deletes a class, because Ant compiles only the changed sources and a stale class file hides a broken reference.
- Follow `docs/standards/`: Allman braces and four spaces, the four import groups, JUnit 4 with `org.junit.Assert`, the test for `S/<pkg>/<Class>.java` in `T/<pkg>/<Class>Test.java`.
- A file copied from a Cassandra original keeps its ASF license header.
- Commit subjects: `type(scope): QATOOLS-123 <subject>`, with `!` on a commit that removes a user-facing option or output field.
- Write no comments in code. The no-comments hooks block them.
- Before Task 1, record the master baseline: `ls build/lib/jars > /tmp/qatools-123-jars-master.txt` after `ant build-test` on master.

## Task 1 — Remove the Thrift mode

**Files:**
- Delete: `S/util/ThriftClient.java`, `S/util/SimpleThriftClient.java`, `S/util/SmartThriftClient.java`, `S/operations/predefined/ThriftInserter.java`, `S/operations/predefined/ThriftReader.java`, `S/operations/predefined/ThriftCounterAdder.java`, `S/operations/predefined/ThriftCounterGetter.java`
- Modify: `S/settings/SettingsMode.java:64,155,202` (the `thrift` option groups), `S/settings/StressSettings.java:36-44,97-130` (the Thrift client getters), `S/settings/SettingsSchema.java:33,71-76,237-300` (`createKeySpacesThrift`), `S/settings/SettingsPort.java:50` (`thrift=`), `S/settings/SettingsTransport.java:41-77,133` (`ITransportFactory`, `factory=`), `S/settings/Legacy.java`, `S/settings/CliOption.java`, `S/settings/SettingsCommandPreDefined.java`, `S/operations/predefined/PredefinedOperation.java`, `S/operations/predefined/CqlOperation.java:356-360,379-383,505-545` (`Cql3CassandraClientWrapper`), `S/operations/userdefined/SchemaQuery.java`, `S/operations/userdefined/ValidatingSchemaQuery.java`, `S/operations/userdefined/SchemaStatement.java`, `S/operations/userdefined/TokenRangeQuery.java`, `S/StressProfile.java:69,467,706` (`thriftInsertId`, `Compression.NONE`), `S/Operation.java`, `S/StressAction.java`, `S/generate/PartitionIterator.java`, `bin/cassandra-stress:74` (`$classes/thrift`)
- Test: `T/settings/SettingsModeTest.java`

**Internals:** `SettingsMode` throws `IllegalArgumentException("Mode thrift was removed. Use -mode native or -mode 4x.")` when the `-mode` arguments hold `thrift`. `SettingsTransport` keeps the SSL options and builds no transport factory.

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

- [x] Add a case to `SettingsModeTest`: `-mode cql3 simplenative` throws `Mode simplenative was removed. Use -mode native or -mode 4x.`
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
- Modify: `S/report/StressMetrics.java:49,61-66,104-119,228-237,251-257,345,354-374,397-399` (the `GcStats` fields, the GC columns of `HEADMETRICS`, `printRow`, the three `Total GC` lines), `S/settings/SettingsPort.java:51` (`jmx=`)
- Test: `T/report/StressMetricsTest.java`

**Internals:** `StressMetrics.HEADMETRICS` ends at `"errors"`. `printRow` drops its `GcStats` parameter.

- [ ] Write `StressMetricsTest`: `HEADMETRICS` equals the fourteen fields of the spec Outputs contract, in order.
- [ ] Run it and confirm the failure.
- [ ] Delete `JmxCollector`, the GC fields, the GC columns, the `Total GC` lines and `-port jmx=`.
- [ ] Run `grep -rn "GcStats\|JmxCollector\|NodeProbe\|jmxPort" S` and confirm no match.
- [ ] Run the verify sequence.
- [ ] Commit `feat(report)!: QATOOLS-123 remove jmx and the gc output`, with the boxes of this task checked.

## Task 5 — Remove `-col super=` and `-col comparator=`

**Files:**
- Modify: `S/settings/SettingsColumn.java:46-140,151-176`
- Test: `T/settings/SettingsColumnTest.java`

**Internals:** `SettingsColumn` builds the column names as UTF-8 bytes, sorted with `ByteBuffer.compareTo`. The `names` and `namestrs` fields stay.

- [ ] Write `SettingsColumnTest`: `-col n=FIXED(3)` gives the names `C0`, `C1`, `C2`. `-col names=b,a` gives `a`, `b`. `-col super=1` and `-col comparator=UTF8Type` throw `IllegalArgumentException`.
- [ ] Run it and confirm the failure of the two removed options.
- [ ] Remove the two options and the `TypeParser`, `AbstractType` and `BytesType` uses from `SettingsColumn`.
- [ ] Run the verify sequence.
- [ ] Commit `feat(settings)!: QATOOLS-123 remove the super and comparator column options`, with the boxes of this task checked.

## Task 6 — Port the marshal types to `stress.marshal`

**Files:**
- Create: `S/marshal/AbstractType.java`, `S/marshal/TypeSerializer.java`, `S/marshal/MarshalException.java`, one class per type that `S/generate/values/*` and `S/generate/PartitionIterator.java` import: `AsciiType`, `UTF8Type`, `BytesType`, `BooleanType`, `ByteType`, `ShortType`, `Int32Type`, `LongType`, `FloatType`, `DoubleType`, `DecimalType`, `IntegerType`, `InetAddressType`, `UUIDType`, `TimeUUIDType`, `DateType`, `SimpleDateType`, `TimeType`, `ListType`, `SetType`, with their serializers from `src/java/org/apache/cassandra/serializers/`
- Modify: every file under `S/generate/values/`, `S/generate/PartitionIterator.java`
- Test: `T/marshal/AbstractTypeTest.java`, fixtures in `test/resources/stress/marshal/`

**Internals:** `AbstractType<T>` implements `Comparator<ByteBuffer>` and holds `decompose`, `compose`, `getString` and `getSerializer`. `ListType.getInstance(AbstractType, boolean)` and `SetType.getInstance(AbstractType, boolean)` keep their signatures. Each serializer body is a copy of the Cassandra original.

- [ ] Generate the master fixture: on master, write a throwaway main that runs `decompose` on fixed values for each type above through the server classes, and store the hex of each result in `test/resources/stress/marshal/master.txt`. Keep the main out of the commit.
- [ ] Generate the snapshot fixture: take one snapshot that SCT lists in `defaults/manager_restore_benchmark_snapshots.yaml`, read 20 partition keys and their rows with `scylla sstable dump-data`, and store the key and value hex with the CQL type in `test/resources/stress/marshal/snapshot.txt`.
- [ ] Write `AbstractTypeTest`: for each fixture line, `decompose` of the value equals the stored bytes, and `compose` of the bytes equals the value.
- [ ] Run it and confirm the failure: `stress.marshal` does not exist.
- [ ] Create the classes, then switch the imports in `S/generate` from `org.apache.cassandra.db.marshal` to `org.apache.cassandra.stress.marshal`.
- [ ] Run `grep -rn "org.apache.cassandra.db.marshal\|org.apache.cassandra.serializers" S` and confirm no match.
- [ ] Run the verify sequence.
- [ ] Commit `refactor(marshal): QATOOLS-123 port the marshal types into stress`, with the boxes of this task checked.

## Task 7 — Port the utility helpers to `stress.util`

**Files:**
- Create: `S/util/ByteBufferUtil.java`, `S/util/FBUtilities.java`, `S/util/Pair.java`, `S/util/UUIDGen.java`, `S/util/MurmurHash.java`, `S/util/DynamicList.java`, `S/util/LockedDynamicList.java`, `S/util/ConsistencyLevel.java`, `S/util/EncryptionOptions.java`, `S/util/SSLFactory.java`, `S/util/FileUtils.java`. Each one keeps only the members that stress calls.
- Modify: the importers that `grep -rln "org.apache.cassandra.utils\|org.apache.cassandra.db.ConsistencyLevel\|org.apache.cassandra.config.EncryptionOptions\|org.apache.cassandra.security\|org.apache.cassandra.io.util.FileUtils\|org.apache.cassandra.concurrent" S` lists, `S/Stress.java:27-31,65-73` (`DatabaseDescriptor.clientInitialization`, `WindowsTimer`), `S/StressServer.java` (`NamedThreadFactory`)

**Internals:** `Stress` drops the Windows timer calls. `StressServer` uses `Executors.newCachedThreadPool` with a thread factory that sets the name. `ConsistencyLevel` keeps the constants and the driver conversion that stress uses.

- [ ] Run the existing tests: `DistributionSequenceTest` covers `DynamicList` through `Seed`, and `SettingsNodeTest` covers the settings. They pass before the change.
- [ ] Create the trimmed copies and switch every import.
- [ ] Run the grep above and confirm no match.
- [ ] Run the verify sequence.
- [ ] Commit `refactor(util): QATOOLS-123 port the utility helpers into stress`, with the boxes of this task checked.

## Task 8 — Read the profile names with `CqlNames`

**Files:**
- Create: `S/util/CqlNames.java`
- Modify: `S/StressProfile.java:43-46,152-200` (`CQLFragmentParser`, `CqlParser`, `RequestValidationException`, `SyntaxException`)
- Test: `T/util/CqlNamesTest.java`

**Internals:** `keyspaceOf` and `tableOf` match one regular expression each, case-insensitive: `CREATE KEYSPACE (IF NOT EXISTS)? <name>` and `CREATE TABLE (IF NOT EXISTS)? (<name>\.)?<name>`, where `<name>` is `"[^"]+"` or `\w+`, with `\s+` between tokens. A quoted name keeps its case. An unquoted name is lower-cased. No match throws `IllegalArgumentException` with the CQL text.

- [ ] Write `CqlNamesTest` with these cases: plain, `IF NOT EXISTS`, mixed letter case, two spaces and a newline between tokens, a quoted name, a qualified `ks.table`, and a statement that matches nothing.
- [ ] Run it and confirm the failure.
- [ ] Create `CqlNames` and switch `StressProfile` to it.
- [ ] Run the verify sequence. `StressProfileTest` must pass.
- [ ] Commit `refactor(profile): QATOOLS-123 read profile names without the cql parser`, with the boxes of this task checked.

## Task 9 — Check the strategies against allow-lists

**Files:**
- Create: `S/settings/ReplicationStrategy.java`, `S/settings/CompactionStrategy.java`
- Modify: `S/settings/OptionReplication.java:30,71-94`, `S/settings/OptionCompaction.java:63-77`
- Test: `T/settings/ReplicationStrategyTest.java`, `T/settings/CompactionStrategyTest.java`

**Internals:** `ReplicationStrategy.validate` accepts the short name or `org.apache.cassandra.locator.<name>` and returns the full name. `CompactionStrategy.validate` accepts the short name or `org.apache.cassandra.db.compaction.<name>` and returns the name as given. Each throws `IllegalArgumentException("Invalid replication strategy: " + name)` or `"Invalid compaction strategy: " + name`.

- [ ] Write the two tests: each allowed name, short and full. `SimpleStrategy`, `LocalStrategy`, `OldNetworkTopologyStrategy` and `java.lang.String` fail with the message.
- [ ] Run them and confirm the failure.
- [ ] Create the enums and switch the two adapters to them.
- [ ] Run the verify sequence.
- [ ] Commit `feat(schema)!: QATOOLS-123 check strategies against fixed lists`, with the boxes of this task checked.

## Task 10 — Read the versions from the manifest and the drivers

**Files:**
- Modify: `S/settings/SettingsMisc.java:128-168` (`maybePrintVersion`, `parseVersionFile`), `build.xml:1026-1034` (`createVersionPropFile`), `build.xml:1113` (its `antcall`), `build.xml:1138-1140` (the `jar` manifest), `build.xml:1204` (the exclude), `build.xml:91,387` (`version.properties.dir`)
- Test: `T/settings/SettingsMiscTest.java`

**Internals:** `SettingsMisc.versionLines(String stressVersion, String driver3Version, String driver4Version)` returns the three lines of the spec Outputs contract. `maybePrintVersion` passes `SettingsMisc.class.getPackage().getImplementationVersion()`, `com.datastax.driver.core.Cluster.getDriverVersion()` and `shaded.com.datastax.oss.driver.api.core.session.Session.OSS_DRIVER_COORDINATES.getVersion().toString()`. The `jar` target adds `<manifest><attribute name="Implementation-Version" value="${version}"/></manifest>`.

- [ ] Write `SettingsMiscTest`: `versionLines("1.0.0", "3.11.5.18", "4.19.2.1")` equals the three lines.
- [ ] Run it and confirm the failure.
- [ ] Add `versionLines`, switch `maybePrintVersion`, remove `parseVersionFile`, `createVersionPropFile` and its property, and add the manifest attribute.
- [ ] Run `ant jar` and then `bin/cassandra-stress version`. Confirm the three lines with real versions.
- [ ] Run the verify sequence.
- [ ] Commit `refactor(version): QATOOLS-123 read versions from the manifest and the drivers`, with the boxes of this task checked.

## Task 11 — Delete the server tree and the server tests

**Files:**
- Delete: every directory under `src/java/org/apache/cassandra/` except `stress/`, `src/java/com/datastax/`, `src/antlr/`, `src/gen-java/`, `interface/`, `src/resources/org/apache/cassandra/cql3/`, `test/distributed`, `test/long`, `test/burn`, `test/microbench`, `test/data`, `test/conf`, `test/resources` except the files that Task 6 adds, every directory under `test/unit/org/apache/cassandra/` except `stress/`, `.build/dependency-check-suppressions.xml`, `eclipse_compiler.properties`, `ide/idea-iml-file.xml`, `NEWS.txt`, `README-cassandra.asc`
- Keep: `src/resources/org/apache/cassandra/stress/graph/graph.html`, `ide/idea/`

- [ ] Run `grep -rnE "import (static )?org\.apache\.cassandra\.[a-z]+" S T | grep -v "org.apache.cassandra.stress"` and confirm no match. Stop and go back to the task that owns a hit.
- [ ] Delete the paths above with `git rm -r`.
- [ ] Run the verify sequence. `build.xml` still declares the server jars, so only the source set changes here.
- [ ] Commit `chore!: QATOOLS-123 delete the vendored cassandra source`, with the boxes of this task checked.

## Task 12 — Trim `build.xml` to the stress dependencies and targets

**Files:**
- Modify: `build.xml:550-984` (`maven-declare-dependencies`), `build.xml:169-170` (`java11-jvmargs`), `build.xml:176-181` (`build.classes.thrift`), `build.xml:1088-1110` (`build_java`), `build.xml:1036-1083,1296-1375` (the test macros: `storage-config`, jamm, the Cassandra system properties), `build.xml:1120-1131` (the POMs), `conf/jvm-clients.options`, `.github/workflows/test.yml:18,30,43,55,106`
- Delete targets: `check-gen-cql3-grammar`, `gen-cql3-grammar`, `maven-ant-tasks-*`, `maven-ant-tasks-retrieve-build`, `echo-base-version` when nothing calls it, `check-gen-thrift-java`, `gen-thrift-java`, `gen-thrift-py`, `test-run`, `test-cdc`, `msg-ser-*`, `cql-test`, `cql-test-some`, `test-jvm-dtest`, `test-jvm-upgrade-dtest`, `mvn-install`, `generate-idea-files`

**Internals:** one POM `cassandra-stress` with the coordinates of the spec table, HdrHistogram at 2.2.2. `<javac>` sets `release="21"` and `<compilerarg value="-proc:none"/>`, and takes no `--add-exports` or `--add-opens`. The `build` target loses the `hotspot_compiler` copy, and `artifacts` loses its exclude. The test macro keeps `-ea`, `-Djava.io.tmpdir` and `-Djava.awt.headless=true`.

- [ ] Run `grep -rn "ant \|make " .github Makefile Dockerfile scripts dist` and list every target that they call. Each one must stay.
- [ ] Rewrite `maven-declare-dependencies` to the spec table and remove the other coordinates and the `parent`, `all` and `thrift` POMs.
- [ ] Remove the targets above and the properties that only they read.
- [ ] Remove from `conf/jvm-clients.options` the flags for JDK internals that only server code used: `jdk.internal.misc`, `jdk.internal.module`, `jdk.internal.ref`, the `java.rmi` and `java.management.rmi` exports, `com.sun.management.internal`, `-Djdk.attach.allowAttachSelf`.
- [ ] Set every `java:` matrix in `.github/workflows/test.yml` to `["21", "25"]`.
- [ ] Run `ant realclean`, then the verify sequence on JDK 21.
- [ ] Run `ls build/lib/jars` and diff it against the master baseline. Record each version change of Netty, Guava and Jackson in the pull request body.
- [ ] Run `make docker-build`, and build the deb and the rpm with `scripts/build_deb.sh` and `scripts/build_rpm.sh`.
- [ ] Commit `build!: QATOOLS-123 keep only the stress dependencies and targets`, with the boxes of this task checked.

## Task 13 — Run on JDK 25

**Files:**
- Modify: `conf/jvm-clients.options` when a driver needs a flag, `build.xml` driver versions when a flag does not suffice

- [ ] Run the verify sequence with `JAVA_HOME` set to Temurin 25.
- [ ] Start Scylla with `compose.yml`, then run each script in `integration-tests/` with the 3.x and the 4.x driver on JDK 21 and on JDK 25.
- [ ] For each failure on JDK 25, add the smallest flag to `conf/jvm-clients.options`, or move to a driver version that runs on JDK 25. Run the step again.
- [ ] Run each integration test once with `-log hdrfile=` and once with `-transport truststore=`, on JDK 25.
- [ ] Run the verify sequence on JDK 21 again.
- [ ] Commit `fix: QATOOLS-123 run the drivers on JDK 25`, with the boxes of this task checked. Skip the commit when no file changed, and check this box.

## Task 14 — Close the spec checks

- [ ] Confirm the spec Contracts against the jar: `bin/cassandra-stress help` lists no removed option, `-mode thrift` and `-mode cql3 simplenative` print the removal message, the interval header ends at `errors`, and `version` prints three lines.
- [ ] Update the spec in the same commit when the build changed a design point, with a `(build)` line in `Decisions`.
- [ ] Update the pull request body: the removed options, the jar changes from Task 12, and `refs QATOOLS-123` as the last line, because the Gradle pull request under the same key finishes the task.
- [ ] Commit the checked boxes of this plan.
