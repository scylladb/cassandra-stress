plugins {
    id("cassandra-stress.java-module")
}

description = "The cassandra-stress client for the Scylla Java driver 3.x."

dependencies {
    implementation(project(":core"))
    api(variantOf(libs.scylla.driver3) { classifier("shaded") }) {
        exclude(group = "io.netty")
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "com.github.jnr")
        exclude(group = "io.dropwizard.metrics", module = "metrics-core")
        exclude(group = "com.google.guava", module = "listenablefuture")
        exclude(group = "com.google.j2objc", module = "j2objc-annotations")
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
        exclude(group = "org.checkerframework", module = "checker-qual")
    }
    implementation(libs.slf4j.api)
    runtimeOnly(libs.lz4)
    runtimeOnly(libs.snappy)
}
