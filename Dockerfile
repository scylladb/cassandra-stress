FROM eclipse-temurin:11-jdk-noble AS build

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

RUN --mount=type=cache,target=/root/.m2 ant realclean && \
    ant ${BUILD_OPTS} && \
    ant ${BUILD_OPTS} -Drelease=true -Dversion="$CASSANDRA_STRESS_VERSION" artifacts && \
    bash ./SCYLLA-VERSION-GEN && \
    cp build/SCYLLA-* build/dist/

FROM eclipse-temurin:11-jre-noble AS production

LABEL org.opencontainers.image.source="https://github.com/scylladb/cassandra-stress"
LABEL org.opencontainers.image.title="ScyllaDB Cassandra Stress"


ENV SCYLLA_HOME=/scylla-tools-java
ENV SCYLLA_CONF=/scylla-tools-java/conf
ENV PATH=$PATH:$SCYLLA_HOME/tools/bin
ENV LD_LIBRARY_PATH="/lib/x86_64-linux-gnu:/usr/local/lib:/usr/lib:/lib:/lib64:/usr/local/lib/x86_64-linux-gnu"
ENV DEBIAN_FRONTEND="noninteractive"
ENV TZ="UTC"

WORKDIR $SCYLLA_HOME

COPY --from=build /app/build/dist .

RUN --mount=type=cache,target=/var/cache/apt ln -snf "/usr/share/zoneinfo/$TZ" /etc/localtime \
    && echo "$TZ" > /etc/timezone \
    && apt update \
    && apt upgrade -y \
    && apt install -y libsnappy-java libsnappy-jni \
    && chmod +x tools/bin/cassandra-stress \
    && chmod +x tools/bin/cassandra-stressd \
    && rm tools/bin/*.bat \
    && apt-get purge -y \
    gcc make g++ apt-transport-https \
    autoconf bzip2 cpp libasan8 m4 libtirpc3 libtsan2 libubsan1 build-essential \
    pkg-config pkgconf pkgconf-bin build-essential \
    && apt-get autoremove -y \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
ENTRYPOINT [ "/bin/bash", "-o", "pipefail", "-c" ]

CMD ["cassandra-stress"]
