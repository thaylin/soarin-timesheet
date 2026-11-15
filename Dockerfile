# syntax=docker/dockerfile:1
#
# Multi-arch build (linux/amd64 + linux/arm64 for Raspberry Pi).
# The build stage runs on the builder's native platform (the jar is
# platform-independent); only the slim runtime stage is per-architecture.

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI (they need Docker for Testcontainers); skip them in the image build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /src/target/soarin-timesheet.jar /app/app.jar
USER app
EXPOSE 8080
# Keep the heap modest so the JVM and PostgreSQL share a Pi comfortably.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+UseSerialGC"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
