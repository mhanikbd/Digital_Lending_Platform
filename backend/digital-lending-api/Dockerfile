# ---- build -----------------------------------------------------------------
# Dependencies are resolved in their own layer so a source-only change does not
# re-download the world on every rebuild.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Integration tests need a Docker daemon, which is not available inside an image
# build. The pipeline runs `mvn verify` before an image is ever built.
RUN mvn -B -q clean package -DskipTests

# ---- runtime ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl backs the container healthcheck against the internal management port.
RUN apk add --no-cache curl \
    && addgroup -S lending \
    && adduser -S -G lending -H -s /sbin/nologin lending

WORKDIR /app
COPY --from=build --chown=lending:lending /build/target/*.jar /app/application.jar

# Never run the API as root.
USER lending

# 8080 serves the API. 9091 serves actuator and is not published by compose.
EXPOSE 8080 9091

ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/application.jar"]
