# ./gradlew dockerImage builds this image from the tree that ./gradlew installDist
# writes, so the image holds the same jars as the archives and the packages.
FROM eclipse-temurin:21.0.12_8-jre-noble

ARG CASSANDRA_STRESS_VERSION

LABEL org.opencontainers.image.source="https://github.com/scylladb/cassandra-stress"
LABEL org.opencontainers.image.title="ScyllaDB Cassandra Stress"
LABEL org.opencontainers.image.version="${CASSANDRA_STRESS_VERSION}"

ENV CASSANDRA_STRESS_HOME="/usr/local/share/cassandra-stress"
ENV PATH="$PATH:$CASSANDRA_STRESS_HOME/bin"
ENV LD_LIBRARY_PATH="/lib/x86_64-linux-gnu:/usr/local/lib:/usr/lib:/lib:/lib64:/usr/local/lib/x86_64-linux-gnu"
ENV DEBIAN_FRONTEND="noninteractive"
ENV TZ="UTC"

WORKDIR $CASSANDRA_STRESS_HOME

RUN ln -snf "/usr/share/zoneinfo/$TZ" /etc/localtime \
    && echo "$TZ" > /etc/timezone \
    && apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y libsnappy-java libsnappy-jni \
    && apt-get purge -y \
    gcc make g++ apt-transport-https \
    autoconf bzip2 cpp libasan8 m4 libtirpc3 libtsan2 libubsan1 build-essential \
    pkg-config pkgconf pkgconf-bin build-essential \
    && apt-get autoremove -y \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

COPY build/install/cassandra-stress .

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
ENTRYPOINT ["cassandra-stress"]
