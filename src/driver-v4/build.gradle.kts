plugins {
    id("cassandra-stress.java-module")
}

description = "The cassandra-stress client for the Scylla Java driver 4.x."

dependencies {
    implementation(project(":core"))
    api(libs.scylla.driver4) {
        exclude(group = "com.typesafe", module = "config")
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "io.dropwizard.metrics", module = "metrics-core")
        exclude(group = "org.hdrhistogram", module = "HdrHistogram")
        exclude(group = "com.github.jnr", module = "jnr-posix")
        exclude(group = "org.reactivestreams", module = "reactive-streams")
    }
    implementation(libs.slf4j.api)
    runtimeOnly(libs.typesafe.config)
    runtimeOnly(libs.lz4)
    runtimeOnly(libs.snappy)
    testRuntimeOnly(libs.reactive.streams)
}
