# Stage 1: Build stage
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Resolve dependencies in their own layer: it is reused until pom.xml changes, so editing source
# does not re-download the dependency tree. No BuildKit cache mount, deliberately: Railway's
# builder only accepts ids of the form s/<service id>-<target>, which cannot be written portably
# (its docs rule out variables in the id), and layer reuse already covers the common case.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup && \
    mkdir -p /app/logs && chown -R appuser:appgroup /app

COPY --from=builder --chown=appuser:appgroup /build/target/spendos.jar /app/spendos.jar

# Switch to non-root user
USER appuser

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/api/v1/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/spendos.jar"]
