# Ledger Chaos Machine

A testing tool for publishing controlled event sequences to Kafka topics consumed by the ledger service. Enables validation of event processing, edge cases, and error handling under various conditions.

## Prerequisites

- Java 25
- Docker (for Kafka)
- Gradle 8.x

## Getting Started

### Build the project

```bash
./gradlew build
```

### Run locally

```bash
./gradlew bootRun
```

The application starts on port 27100 by default.

### Access Swagger UI

Navigate to: http://localhost:27100/swagger-ui.html

### Health Check

Navigate to: http://localhost:27100/actuator/health

## Configuration

Configuration is managed through `application.yml` and environment variables:

- `SERVER_PORT` - HTTP server port (default: 27100)
- `CHAOS_DATASOURCE_PATH` - SQLite database file path (default: ./data/chaos.db)
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker addresses (default: 127.0.0.1:9092)
- `CHAOS_TARGET_LABEL` - Cluster label for safety (default: local)

## Testing

Run unit tests:
```bash
./gradlew test
```

Run integration tests:
```bash
./gradlew integrationTest
```

Run all tests:
```bash
./gradlew check
```

## Database Management

The application uses SQLite for persistence. To reset local state:

```bash
rm -rf data/chaos.db
```

The database will be recreated on next startup with migrations applied.

## Docker

Build the image:
```bash
docker build -t ledger-chaos-machine:latest .
```

Run the container:
```bash
docker run -p 27100:27100 \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  -v $(pwd)/data:/app/data \
  ledger-chaos-machine:latest
```

## Development

The project uses:
- Spring Boot 4.0.6
- Java 25 with virtual threads
- SQLite with JPA and Flyway
- Kafka for event publishing
- Record builders for immutable DTOs

## Architecture

- `com.softspark.chaos.base` - Shared base classes and utilities
- `com.softspark.chaos.config` - Spring configuration
- `com.softspark.chaos.advice` - Global exception handling
- `com.softspark.chaos.exception` - Custom exceptions
- `com.softspark.chaos.kafka` - Kafka producer and event envelope
