# syntax=docker/dockerfile:1
#
# Production image of Swift Invoicing.
#   docker build -t swift-invoicing .
#   docker run -p 7070:7070 -e DB_URL=jdbc:postgresql://db:5432/invoicing -e DB_USER=... -e DB_PASSWORD=... \
#              -e APP_BASE_URL=https://factures.example.dz -e MAIL_HOST=... swift-invoicing
# Required settings and the checks done at startup: see application.properties and ProductionSettingsCheck.

# ---- Build: compile, build the CSS (Tailwind CLI) and package the jar ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Maven wrapper and dependencies first: this layer is reused while pom.xml does not change
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (they need Docker for the PostgreSQL ones); the cache also keeps the Tailwind CLI
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/swift-invoicing-*.jar extract --layers --launcher --destination target/extracted

# ---- Runtime: JRE only, non-root user ----
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --home /app --shell /usr/sbin/nologin app

WORKDIR /app
# One layer per Spring Boot jar layer: a code change only rebuilds the last (small) one
COPY --from=build --chown=app:app /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/application/ ./

USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

EXPOSE 7070

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:7070/actuator/health/liveness > /dev/null || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
