FROM amazoncorretto:11-al2-native-headless as build

WORKDIR /app

COPY . .

RUN apk update \
    && apk upgrade \
    && apk add apache-ant \
    && ant articafts


FROM amazoncorretto:11-al2-native-headless as production

WORKDIR /app

COPY --from=build /app .