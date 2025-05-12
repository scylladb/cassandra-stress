FROM eclipse-temurin:21.0.7_6-jdk-noble AS build

ENV LD_LIBRARY_PATH="/lib/x86_64-linux-gnu:/usr/local/lib:/usr/lib:/lib:/lib64:/usr/local/lib/x86_64-linux-gnu"
ENV DEBIAN_FRONTEND="noninteractive"
ENV TZ="UTC"

WORKDIR /app

RUN --mount=type=cache,target=/var/cache/apt ln -snf "/usr/share/zoneinfo/$TZ" /etc/localtime \
    && echo "$TZ" > /etc/timezone \
    && apt update \
    && apt install -y ant

COPY . .

ARG CASSANDRA_STRESS_VERSION=666.development
ARG BUILD_OPTS

RUN --mount=type=cache,target=/root/.m2 ant realclean \
    && ant ${BUILD_OPTS} \
    && ant ${BUILD_OPTS} -Drelease=true -Drelease.version=21 -Dversion="${CASSANDRA_STRESS_VERSION}" artifacts \
    && chmod +x build/dist/bin/cassandra-stress


FROM eclipse-temurin:21.0.7_6-jre-noble AS production

LABEL org.opencontainers.image.source="https://github.com/scylladb/cassandra-stress"
LABEL org.opencontainers.image.title="ScyllaDB Cassandra Stress"


ENV CASSANDRA_STRESS_HOME="/usr/local/share/cassandra-stress"
ENV PATH="$PATH:$CASSANDRA_STRESS_HOME/bin"
ENV LD_LIBRARY_PATH="/lib/x86_64-linux-gnu:/usr/local/lib:/usr/lib:/lib:/lib64:/usr/local/lib/x86_64-linux-gnu"
ENV DEBIAN_FRONTEND="noninteractive"
ENV TZ="UTC"

WORKDIR $CASSANDRA_STRESS_HOME

COPY --from=build /app/build/dist .

RUN --mount=type=cache,target=/var/cache/apt ln -snf "/usr/share/zoneinfo/$TZ" /etc/localtime \
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

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
ENTRYPOINT ["cassandra-stress"]
