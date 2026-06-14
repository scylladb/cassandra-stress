#!/usr/bin/env bash
# port-and-test.sh
# Documents what was ported from master → next and verifies the build + tests pass.
#
# Ported features (master commits since v3.20.0 divergence):
#   55dae3f - fix(ssl): TLS 1.3 / ECDHE cipher defaults (SettingsTransport.java)
#   1318906 - feat: remote-dc option for multi-DC failover (SettingsNode.java, LoadBalanceType.java)
#   1d1ee83 - feat: requestTimeout CLI arg (SettingsMode.java, JavaDriverClient.java, JavaDriverV4Client.java)
#   4199b95 - fix: driver 4.x request timeout default 12s (JavaDriverV4Client.java)
#   c214007 - fix: column name case sensitivity in user profiles (StressProfile.java)
#   26f8e56 - fix: TimestampCodec code quality (TimestampCodec.java)
#   40bca51 - feat: Java Driver 4.x version reporting (SettingsMisc.java)
#   e845a95 - chore: scylla-driver-core → 3.11.5.15 (build.gradle.kts)
#   2844c5b - chore: java-driver-core → 4.19.0.9 (build.gradle.kts)
#
# Package mapping applied (next uses com.scylladb.* and method-style accessors):
#   org.apache.cassandra.stress.* → com.scylladb.stress.*
#   settings.node.x              → settings.node().x
#   settings.mode.x              → settings.mode().x
#   settings.rate.x              → settings.rate().x
#
# Pre-existing build errors also fixed (next branch was never compiling):
#   SSLFactory       - created com.scylladb.utils.SSLFactory (JDK-native, no Cassandra dep)
#   JmxCollector     - created stub in util package (NodeProbe removed; GC stats unavailable)
#   OptionCompaction - removed ConfigurationException / createCompactionStrategy (commented in master)
#   OptionReplication- removed AbstractReplicationStrategy class-check; accepts any name
#   StressProfile    - removed CQLFragmentParser/CqlParser validation blocks (unavailable)
#   StressServer     - replaced NamedThreadFactory.createThread with new Thread(...)
#   UUIDGen          - replaced FBUtilities/NativeLibrary with JDK NetworkInterface/ProcessHandle
#   FSException      - changed to extend RuntimeException (was incorrectly checked)
#   ConsistencyLevel - added isDatacenterLocal() and isSerialConsistency() methods
#   Sets/Lists       - fixed unchecked Class<Set<T>> cast via (Class<Set<T>>)(Class<?>)Set.class
#   PartitionIterator- replaced AbstractType/BytesType with Type<?>; added getString() to Type
#   SetSerializer    - fixed getInstance() call: added required Comparator<ByteBuffer> parameter
#   ListSerializer   - replaced non-existent ListType with ListSerializer

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Building cassandra-stress (next branch) ==="
./gradlew compileJava compileTestJava

echo ""
echo "=== Running unit tests ==="
./gradlew test

echo ""
echo "=== Building fat JAR ==="
./gradlew shadowJar

echo ""
echo "=== All checks passed ==="
echo "Fat JAR: $(ls build/libs/*-all.jar 2>/dev/null || ls build/libs/*.jar | grep -v sources | grep -v javadoc | head -1)"
