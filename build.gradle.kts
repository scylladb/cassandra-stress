plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.3.1"
}

group = "com.scylladb"
version = "4.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
}

application {
    mainClass.set("com.scylladb.stress.Stress")
}

tasks.withType<CreateStartScripts>().configureEach {
    mainClass.set(application.mainClass)
}

repositories {
    mavenCentral()
}


dependencies {
    implementation("com.scylladb:scylla-driver-core:3.11.5.11:shaded") {
        exclude(group = "io.netty")
        exclude(group = "io.dropwizard.metrics", module = "metrics-core")
    }
    implementation("com.scylladb:java-driver-core:4.19.0.1")

    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("commons-cli:commons-cli:1.1")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:log4j-over-slf4j:2.0.9")
    implementation("org.slf4j:jcl-over-slf4j:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("joda-time:joda-time:2.4")
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("com.googlecode.json-simple:json-simple:1.1")
    implementation("org.jctools:jctools-core:1.2.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}