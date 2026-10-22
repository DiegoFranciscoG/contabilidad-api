# syntax=docker/dockerfile:1

# ---- Build: compiles the jar and splits it into layers (tests run in CI, not here) ----
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp -DskipTests package \
    && java -Djarmode=tools -jar target/contabilidad-api-1.0.0.jar extract --layers --destination extracted

# ---- Runtime: JRE only, non-root user, healthcheck ----
FROM eclipse-temurin:26.0.2_10-jre-alpine-3.24
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT:-8080}/actuator/health" > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "contabilidad-api-1.0.0.jar"]
