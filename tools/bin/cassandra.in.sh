# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

SCYLLA_HOME="$(dirname "$0")"

while [ "$SCYLLA_HOME" != "/" ]; do
  if [ -d "$SCYLLA_HOME/conf" ]; then
    break
  fi
  SCYLLA_HOME="$(dirname "$SCYLLA_HOME")"
done

if [ "$SCYLLA_HOME" = "/" ]; then
  echo "Error: 'conf' directory not found in any parent directory."
  exit 1
fi

if [ "$SCYLLA_CONF" = "" ]; then
  SCYLLA_CONF="$SCYLLA_HOME/conf"
fi

# The java classpath (required)
CLASSPATH="$SCYLLA_CONF"

# This can be the path to a jar file, or a directory containing the
# compiled classes. NOTE: This isn't needed by the startup script,
# it's just used here in constructing the classpath.
if [ -d "$SCYLLA_HOME/build" ]; then
  cassandra_bin="$SCYLLA_HOME/build/classes/main"
  cassandra_bin="$cassandra_bin:$SCYLLA_HOME/build/classes/thrift"
  # shellcheck disable=SC2006
  cassandra_bin="$cassandra_bin:$(ls -1 $SCYLLA_HOME/build/apache-cassandra*.jar 2>/dev/null)"
  cassandra_bin="$cassandra_bin:$SCYLLA_HOME/build/classes/stress"
  CLASSPATH="$CLASSPATH:$cassandra_bin"
fi

# the default location for commitlogs, sstables, and saved caches
# if not set in cassandra.yaml
cassandra_storagedir="$SCYLLA_HOME/data"

# JAVA_HOME can optionally be set here
#JAVA_HOME=/usr/local/jdk6

CONFIG_FILE_REALPATH=$(realpath "$SCYLLA_CONF/cassandra.yaml")
# If scylla.yaml is found - use it as the config file.
if [ -f "$SCYLLA_CONF/scylla.yaml" ]; then
  CONFIG_FILE_REALPATH=$(realpath "$SCYLLA_CONF/scylla.yaml")
fi

CONFIGURATION_FILE_OPT="-Dcassandra.config=file://$CONFIG_FILE_REALPATH"

CLASSPATH="${CLASSPATH}:$(find "$SCYLLA_HOME/lib" "$SCYLLA_HOME/build/lib" "$SCYLLA_HOME/tools" -name '*.jar' -print 2>/dev/null | paste -sd ":" -)"
CLASSPATH="${CLASSPATH#:}"

# Use JAVA_HOME if set, otherwise look for java in PATH
if [ -n "$JAVA_HOME" ]; then
  # Why we can't have nice things: Solaris combines x86 and x86_64
  # installations in the same tree, using an unconventional path for the
  # 64bit JVM.  Since we prefer 64bit, search the alternate path first,
  # (see https://issues.apache.org/jira/browse/SCYLLA-4638).
  for java in "$JAVA_HOME"/bin/amd64/java "$JAVA_HOME"/bin/java; do
    if [ -x "$java" ]; then
      JAVA="$java"
      break
    fi
  done
else
  JAVA=java
fi

if [ -z $JAVA ]; then
  echo Unable to find java executable. Check JAVA_HOME and PATH environment variables. >&2
  exit 1
fi

# Determine the sort of JVM we'll be running on.
java_ver_output=$("${JAVA:-java}" -version 2>&1)
jvmver=$(echo "$java_ver_output" | grep '[openjdk|java] version' | awk -F'"' 'NR==1 {print $2}' | cut -d- -f1)
JVM_VERSION=${jvmver%_*}

if [ "$JVM_VERSION" \< "11" ]; then
  echo "Cassandra-Stress requires Java 11 (or newer)."
  exit 1
fi

jvm=$(echo "$java_ver_output" | grep -A 1 '[openjdk|java] version' | awk 'NR==2 {print $1}')
case "$jvm" in
OpenJDK)
  JVM_VENDOR=OpenJDK
  # this will be "64-Bit" or "32-Bit"
  JVM_ARCH=$(echo "$java_ver_output" | awk 'NR==3 {print $2}')
  ;;
"Java(TM)")
  JVM_VENDOR=Oracle
  # this will be "64-Bit" or "32-Bit"
  JVM_ARCH=$(echo "$java_ver_output" | awk 'NR==3 {print $3}')
  ;;
*)
  # Help fill in other JVM values
  JVM_VENDOR=other
  JVM_ARCH=unknown
  ;;
esac

# Read user-defined JVM options from jvm-server.options file
JVM_OPTS_FILE=$SCYLLA_CONF/jvm11${jvmoptions_variant:--clients}.options

for opt in $(grep "^-" "$JVM_OPTS_FILE"); do
  JVM_OPTS="$JVM_OPTS $opt"
done
