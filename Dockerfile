FROM amazoncorretto:11-alpine AS build

ARG CASSANDRA_STRESS_VERSION

WORKDIR /app

COPY . .

RUN apk update \
    && apk upgrade \
    && apk add apache-ant bash \
    && ant realclean \
    && mkdir -p build lib \
    && ant -Drelease=true -Dversion="$CASSANDRA_STRESS_VERSION" artifacts \
    && bash ./SCYLLA-VERSION-GEN \
    && cp build/SCYLLA-* build/dist/

FROM amazoncorretto:11-alpine AS production

ENV SCYLLA_HOME=/scylla-tools-java
ENV SCYLLA_CONF=/scylla-tools-java/conf

WORKDIR $SCYLLA_HOME

ENV PATH=$PATH:$SCYLLA_HOME/tools/bin

COPY --from=build /app/build/dist .

RUN apk update \
    && apk upgrade \
    && apk add bash \
    && chmod +x tools/bin/cassandra-stress \
    && chmod +x tools/bin/cassandra-stressd \
    && rm tools/bin/*.bat

SHELL [ "/bin/bash" ]

CMD ["cassandra-stress"]