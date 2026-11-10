package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Installs the deb or the rpm with a Temurin runtime in a clean distribution image, and upgrades a
 * deb install of the last release before the scylladb- prefix.
 */
@Tag("package")
class PackageIT {
    private static final Path PACKAGES = Path.of(System.getProperty("stress.package.dir", "build/distributions"));
    private static final String IMAGES =
            System.getProperty("stress.package.images", "ubuntu:24.04,debian:bookworm-slim,fedora:41,fedora:42");
    private static final String JAVA = System.getProperty("stress.package.java", "21,25");

    private static final String INSTALL_DEB_RUNTIME = """
        set -e
        apt-get update
        apt-get install -y wget gpg
        wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
          | gpg --dearmor > /etc/apt/trusted.gpg.d/adoptium.gpg
        . /etc/os-release
        echo "deb https://packages.adoptium.net/artifactory/deb $VERSION_CODENAME main" \
          > /etc/apt/sources.list.d/adoptium.list
        apt-get update
        apt-get install -y temurin-$1-jre
        """;

    private static final String INSTALL_DEB = INSTALL_DEB_RUNTIME + """
        apt-get install -y "$2" "$3"
        """;

    private static final String INSTALL_RPM = """
        set -e
        . /etc/os-release
        cat > /etc/yum.repos.d/adoptium.repo <<EOF
        [Adoptium]
        name=Adoptium
        baseurl=https://packages.adoptium.net/artifactory/rpm/$ID/\\$releasever/\\$basearch
        enabled=1
        gpgcheck=1
        gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public
        EOF
        dnf install -y temurin-$1-jre
        dnf install -y "$2"
        """;

    /** The last release whose deb package is named cassandra-stress. */
    private static final String UNPREFIXED_DEB_URL = "https://github.com/scylladb/cassandra-stress/releases/download/"
            + "v4.1.0/cassandra-stress-java21_4.1.0_all.deb";

    private static final String UPGRADE_DEB = """
        set -e
        apt-get install -y apt-utils
        wget -qO /tmp/old.deb "$1"
        apt-get install -y /tmp/old.deb
        mkdir /tmp/repo
        cp "$2" "$3" /tmp/repo/
        cd /tmp/repo
        apt-ftparchive packages . > Packages
        echo "deb [trusted=yes] file:/tmp/repo ./" > /etc/apt/sources.list.d/local.list
        apt-get update
        apt-get upgrade -y --with-new-pkgs
        """;

    static Stream<Arguments> targets() {
        return Arrays.stream(IMAGES.split(","))
                .map(String::strip)
                .filter(image -> !image.isEmpty())
                .flatMap(image -> Arrays.stream(JAVA.split(","))
                        .map(String::strip)
                        .filter(java -> !java.isEmpty())
                        .map(java -> Arguments.of(image, java)));
    }

    @ParameterizedTest(name = "{0} with Java {1}")
    @MethodSource("targets")
    void installsAndRuns(String image, String java) throws Exception {
        try (GenericContainer<?> os =
                new GenericContainer<>(DockerImageName.parse(image)).withCommand("sleep", "infinity")) {
            os.start();
            boolean deb = os.execInContainer("sh", "-c", "command -v apt-get").getExitCode() == 0;
            String target = copy(os, newest("scylladb-cassandra-stress", deb ? ".deb" : ".rpm"));

            ExecResult install = deb
                    ? os.execInContainer(
                            "sh",
                            "-c",
                            INSTALL_DEB,
                            "install",
                            java,
                            target,
                            copy(os, newest("cassandra-stress_", ".deb")))
                    : os.execInContainer("sh", "-c", INSTALL_RPM, "install", java, target);
            assertEquals(0, install.getExitCode(), install::toString);

            if (deb) {
                // The transitional package installs next to the package it depends on.
                ExecResult transitional = os.execInContainer("dpkg-query", "-W", "-f=${Status}", "cassandra-stress");
                assertEquals("install ok installed", transitional.getStdout(), transitional::toString);
            }

            ExecResult version = os.execInContainer("cassandra-stress", "version");
            String output = version.getStdout() + version.getStderr();
            assertEquals(0, version.getExitCode(), output);
            assertTrue(output.contains("Version: " + System.getProperty("stress.version")), output);
            assertTrue(
                    output.lines().anyMatch(l -> l.matches("scylla-java-driver: \\d+\\.\\d+\\.\\d+\\.\\d+")), output);

            // A service manager can start the command without HOME.
            ExecResult withoutHome = os.execInContainer("env", "-u", "HOME", "cassandra-stress", "version");
            assertEquals(0, withoutHome.getExitCode(), withoutHome::toString);

            ExecResult prefixed = os.execInContainer("scylladb-cassandra-stress", "version");
            assertEquals(0, prefixed.getExitCode(), prefixed::toString);
        }
    }

    @ParameterizedTest(name = "{0} with Java {1}")
    @MethodSource("targets")
    void upgradesTheUnprefixedDeb(String image, String java) throws Exception {
        try (GenericContainer<?> os =
                new GenericContainer<>(DockerImageName.parse(image)).withCommand("sleep", "infinity")) {
            os.start();
            Assumptions.assumeTrue(
                    os.execInContainer("sh", "-c", "command -v apt-get").getExitCode() == 0, "deb images only");

            ExecResult runtime = os.execInContainer("sh", "-c", INSTALL_DEB_RUNTIME, "runtime", java);
            assertEquals(0, runtime.getExitCode(), runtime::toString);

            ExecResult upgrade = os.execInContainer(
                    "sh",
                    "-c",
                    UPGRADE_DEB,
                    "upgrade",
                    UNPREFIXED_DEB_URL,
                    copy(os, newest("scylladb-cassandra-stress", ".deb")),
                    copy(os, newest("cassandra-stress_", ".deb")));
            assertEquals(0, upgrade.getExitCode(), upgrade::toString);

            String expected = System.getProperty("stress.version");
            for (String pkg : new String[] {"scylladb-cassandra-stress", "cassandra-stress"}) {
                ExecResult status = os.execInContainer("dpkg-query", "-W", "-f=${Status} ${Version}", pkg);
                assertEquals("install ok installed " + expected, status.getStdout(), status::toString);
            }

            ExecResult version = os.execInContainer("cassandra-stress", "version");
            String output = version.getStdout() + version.getStderr();
            assertEquals(0, version.getExitCode(), output);
            assertTrue(output.contains("Version: " + expected), output);
        }
    }

    private static String copy(GenericContainer<?> os, Path pkg) {
        String target = "/tmp/" + pkg.getFileName();
        os.copyFileToContainer(MountableFile.forHostPath(pkg), target);
        return target;
    }

    private static Path newest(String prefix, String extension) throws IOException {
        try (Stream<Path> files = Files.list(PACKAGES)) {
            return files.filter(f -> {
                        String name = String.valueOf(f.getFileName());
                        return name.startsWith(prefix) && name.endsWith(extension);
                    })
                    .max(Comparator.comparingLong(f -> f.toFile().lastModified()))
                    .orElseThrow(() ->
                            new IllegalStateException("No " + prefix + "*" + extension + " package in " + PACKAGES));
        }
    }
}
