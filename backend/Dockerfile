# syntax=docker/dockerfile:1
# ControlMap backend — multi-stage build: compile the Spring Boot jar, then run it on a slim JRE.

# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Resolve dependencies first (cached layer) so source-only changes don't re-download the world.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests need a live Postgres + seed env; they run in CI, not in the image build.
RUN mvn -B -q clean package -DskipTests

# --- runtime stage ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# Run as a non-root user (defence in depth — the app never needs root).
RUN groupadd --system app && useradd --system --gid app --home /app app
COPY --from=build /build/target/controlmap-backend-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
