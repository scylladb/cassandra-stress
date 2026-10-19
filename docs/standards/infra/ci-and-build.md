## CI and build

### Pinned actions

Pin each external GitHub Action and each external reusable workflow to a
full commit SHA. Add the tag or the branch of that SHA as a comment after
it. A local reference, as in `uses: ./.github/workflows/build.yml`, has no
pin.

    - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1

### Renovate dependencies

Renovate updates the GitHub Actions, the Docker base images, the Gradle
wrapper, and every dependency and plugin in `gradle/libs.versions.toml`. Its
pull requests use the `chore(deps):` subject.

### Gradle build

Gradle is the build tool. Use the wrapper, `./gradlew`. The build has one
subproject in `src/<name>` for each Java module. The convention plugins in `build-logic`
hold the compile, test and lint setup that every module shares. The root
`build.gradle.kts` holds the coverage report, the archives and the packages.
`gradle/libs.versions.toml` holds every dependency and plugin version. Add a
build step as a Gradle task, and add a step for every module to the
convention plugin. Gradle also builds the
deb and the rpm with the nebula ospackage plugin, so no packaging step runs
outside Gradle. CI tests the launcher with `distributionTest` and the packages
with `packageTest`. Add a test of the build output as a JUnit test in
`src/core/src/integrationTest`, not as a shell script.
`./gradlew dockerImage` builds the Docker image from the `installDist` tree,
so the Dockerfile copies that tree and compiles nothing.
ktlint formats the Gradle build scripts and the Kotlin code in `build-logic`.
`./gradlew modulePathCheck` fails when a jar in `lib/` has no module name.
Add such a jar to `AUTOMATIC_MODULE_NAMES` in `build-logic`.

### Versions

A release is a `v<major>.<minor>.<patch>` git tag. The build takes the
version from the latest such tag, so keep the version out of the build files.
A commit after the tag builds the next patch version with `-SNAPSHOT`.
`-Pversion=<version>` sets the version for one build, as the release workflow
does with the tag.
