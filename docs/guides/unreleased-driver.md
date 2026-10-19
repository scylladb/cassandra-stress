# Build with an unreleased Scylla Java driver

Use this procedure to run cassandra-stress with a driver build that is not on
Maven Central. Examples are a driver fix before its release, or a driver
branch that needs a load test.

The build gets the drivers from Maven Central. The `-PmavenLocal` property
adds the local Maven repository (`~/.m2/repository`) before Maven Central,
for the `com.scylladb` group only. All other dependencies still come from
Maven Central.

| Driver | Repository branch | Artifact | Version property |
| --- | --- | --- | --- |
| 3.x | `scylladb/java-driver`, `scylla-3.x` | `com.scylladb:scylla-driver-core`, classifier `shaded` | `scyllaDriver3Version` |
| 4.x | `scylladb/java-driver`, `scylla-4.x` | `com.scylladb:java-driver-core-shaded` | `scyllaDriver4Version` |

## Install the driver in the local Maven repository

1. Clone the driver repository and check out the branch or commit to test.

    ``` shell
    git clone https://github.com/scylladb/java-driver.git
    cd java-driver
    git checkout scylla-3.x
    ```

2. Read the driver version from the `<version>` element of the root
   `pom.xml`, for example `3.11.5.21-SNAPSHOT`.

3. For driver 3.x, install the core module and the modules it needs.

    ``` shell
    mvn install -DskipTests -Dmaven.javadoc.skip=true -pl driver-core -am
    ```

4. For driver 4.x, install the shaded core module and the modules it needs.

    ``` shell
    mvn install -DskipTests -Dmaven.javadoc.skip=true -pl core-shaded -am
    ```

   On JDK 21 or later, the format check of driver 4.x fails with
   `NoSuchMethodError` in `fmt-maven-plugin`. Use JDK 11 or 17, or add
   `-Dfmt.skip=true` to the command.

## Build cassandra-stress with the driver

1. Build the distribution with the driver version from the local repository.
   For driver 3.x:

    ``` shell
    ./gradlew installDist -PmavenLocal -PscyllaDriver3Version=3.11.5.21-SNAPSHOT
    ```

   For driver 4.x:

    ``` shell
    ./gradlew installDist -PmavenLocal -PscyllaDriver4Version=4.19.2.3-SNAPSHOT
    ```

2. Make sure that the `version` command shows the driver versions you set.

    ``` shell
    build/install/cassandra-stress/bin/cassandra-stress version
    ```

3. Run cassandra-stress from `build/install/cassandra-stress/bin/cassandra-stress`.
   Select driver 3.x with `-mode cql3 native` and driver 4.x with
   `-mode cql3 4x`.

The same properties apply to every task. For example, `./gradlew assemble`
makes the archives, the deb and the rpm, and `./gradlew dockerImage` makes
the Docker image with that driver. `./gradlew integrationTest` runs the
integration tests with that driver.

## Troubleshooting

- `Could not find com.scylladb:scylla-driver-core:<version>`: the build did
  not find the version in the local repository. Make sure that you set
  `-PmavenLocal` and that the version is the same as in the driver `pom.xml`.
- Gradle ignores a module in the local repository when the plain jar is not
  there. Install the driver with `mvn install`. Do not copy only the
  `-shaded` jar into `~/.m2`.
- To use a local repository at another path, add
  `-Dmaven.repo.local=<path>` to the Maven and the Gradle commands.
