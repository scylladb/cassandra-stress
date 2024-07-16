FROM amazoncorretto:11-alpine AS build

ARG VERISON

WORKDIR /app

COPY . .

RUN apk update \
    && apk upgrade \
    && apk add apache-ant bash \
    && ant realclean \
    && mkdir -p build lib \
    && ant -Dversion=${VERSION} maven-declare-dependencies artifacts \
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
    && chmod +x tools/bin/cassandra-stress \
    && chmod +x tools/bin/cassandra-stressd \
    && rm tools/bin/*.bat

CMD ["cassandra-stress"]