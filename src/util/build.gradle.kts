plugins {
    id("cassandra-stress.java-module")
}

description = "Shared helpers of cassandra-stress."

dependencies {
    implementation(libs.slf4j.api)
}
