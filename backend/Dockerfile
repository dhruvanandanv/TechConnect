# ==============================================================================
# TechConnect Backend Production Dockerfile (Spring Boot 3.3.4 / Java 21)
# Multi-stage build with non-root runtime security
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Application
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace/app

# Copy Maven wrapper and POM to cache dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build production jar
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Minimal Production Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

# Install curl for container health check and create dedicated non-root user
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 -g root spring

WORKDIR /app

# Copy packaged jar from builder stage
COPY --from=builder /workspace/app/target/techconnect-backend-*.jar app.jar

# Enforce non-root ownership
RUN chown -R spring:root /app

USER spring:root

# Production JVM flags for container awareness
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

EXPOSE 8080

# Production Healthcheck via Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
