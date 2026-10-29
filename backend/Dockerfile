# syntax=docker/dockerfile:1

# ---- build -----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI and locally against embedded PostgreSQL; the image build only packages.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/devarena-backend-*.jar extract --layers --launcher --destination target/extracted

# ---- runtime ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /src/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /src/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /src/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /src/target/extracted/application/ ./
USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    # Sized for a 512 MB free-tier instance.
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"

# Render injects PORT; application.yml binds server.port to it (default 8080).
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s CMD wget -qO- http://localhost:${PORT:-8080}/actuator/health || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
