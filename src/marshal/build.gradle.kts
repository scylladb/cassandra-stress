plugins {
    id("cassandra-stress.java-module")
}

description = "CQL type serialization of cassandra-stress."

dependencies {
    api(project(":util"))
}
