package org.apache.cassandra.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Installs the deb or the rpm with a Temurin runtime in a clean distribution image. */
@Tag("package")
class PackageIT {
    private static final Path PACKAGES = Path.of(System.getProperty("stress.package.dir", "build/distributions"));
    private static final String IMAGES =
            System.getProperty("stress.package.images", "ubuntu:24.04,debian:bookworm-slim,fedora:41,fedora:42");
    private static final String JAVA = System.getProperty("stress.package.java", "21,25");

    private static final String INSTALL_DEB = """
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
        apt-get install -y "$2"
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
            Path pkg = newest(deb ? ".deb" : ".rpm");
            String target = "/tmp/" + pkg.getFileName();
            os.copyFileToContainer(MountableFile.forHostPath(pkg), target);

            ExecResult install =
                    os.execInContainer("sh", "-c", deb ? INSTALL_DEB : INSTALL_RPM, "install", java, target);
            assertEquals(0, install.getExitCode(), install::toString);

            ExecResult version = os.execInContainer("cassandra-stress", "version");
            String output = version.getStdout() + version.getStderr();
            assertEquals(0, version.getExitCode(), output);
            assertTrue(output.contains("Version: " + System.getProperty("stress.version")), output);
            assertTrue(
                    output.lines().anyMatch(l -> l.matches("scylla-java-driver: \\d+\\.\\d+\\.\\d+\\.\\d+")), output);

            // A service manager can start the command without HOME.
            ExecResult withoutHome = os.execInContainer("env", "-u", "HOME", "cassandra-stress", "version");
            assertEquals(0, withoutHome.getExitCode(), withoutHome::toString);
        }
    }

    private static Path newest(String extension) throws IOException {
        try (Stream<Path> files = Files.list(PACKAGES)) {
            return files.filter(f -> String.valueOf(f.getFileName()).endsWith(extension))
                    .max(Comparator.comparingLong(f -> f.toFile().lastModified()))
                    .orElseThrow(() -> new IllegalStateException("No " + extension + " package in " + PACKAGES));
        }
    }
}
