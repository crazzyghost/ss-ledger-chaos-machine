# Multi-stage Dockerfile for the Ledger Chaos Machine
# Build stage
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY src src

RUN ./gradlew build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:25-jre

# Create non-root user
RUN groupadd -r chaos && useradd -r -g chaos chaos

WORKDIR /app

# Copy built artifact
COPY --from=builder /app/build/libs/*.jar app.jar

# Create data directory
RUN mkdir -p /app/data && chown -R chaos:chaos /app

USER chaos

EXPOSE 27100

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:27100/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
