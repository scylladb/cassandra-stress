FROM amazoncorretto:11-alpine AS build

WORKDIR /app

COPY . .

RUN apk update \
    && apk upgrade \
    && apk add apache-ant \
    && ant realclean \
    && mkdir -p build lib \
    && ant maven-declare-dependencies artifacts


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
    && chmod +x tools/bin/cassandra.in.sh \
    && rm tools/bin/*.bat

CMD ["cassandra-stress"]