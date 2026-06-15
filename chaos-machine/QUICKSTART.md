# Quick Start Guide

## Prerequisites

- Java 25
- Docker (optional, for Kafka)

## Build

```bash
./gradlew build
```

## Run Locally

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The application will start on port **27100**.

## Verify Installation

### Health Check
```bash
curl http://localhost:27100/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### API Health
```bash
curl -u admin:admin http://localhost:27100/api/v0/health
```

Expected response:
```json
{
  "status": "UP",
  "timestamp": "2026-06-15T22:00:00Z",
  "clusterLabel": "local"
}
```

### Swagger UI
Open in browser:
```
http://localhost:27100/swagger-ui.html
```

## Run Tests

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew integrationTest

# All tests
./gradlew check
```

## Docker

### Build Image
```bash
docker build -t ledger-chaos-machine:latest .
```

### Run Container
```bash
docker run -d \
  --name chaos-machine \
  -p 27100:27100 \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  -v $(pwd)/data:/app/data \
  ledger-chaos-machine:latest
```

### Check Logs
```bash
docker logs -f chaos-machine
```

### Stop Container
```bash
docker stop chaos-machine
docker rm chaos-machine
```

## Configuration

Key environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 27100 | HTTP server port |
| `CHAOS_DATASOURCE_PATH` | ./data/chaos.db | SQLite database file path |
| `KAFKA_BOOTSTRAP_SERVERS` | 127.0.0.1:9092 | Kafka broker addresses |
| `CHAOS_TARGET_LABEL` | local | Cluster label (safety) |

## Authentication

Default credentials (dev profile):
- Username: `admin`
- Password: `admin`

## Database Management

Reset local database:
```bash
rm -rf data/chaos.db
```

The database will be recreated on next startup.

## Troubleshooting

### Port already in use
```bash
# Find process using port 27100
lsof -i :27100

# Kill the process (replace PID)
kill -9 <PID>
```

### Kafka connection issues
- Ensure Kafka is running on localhost:9092
- Or set `KAFKA_BOOTSTRAP_SERVERS` to your Kafka broker address

### Database locked
- Check that only one instance is running
- Delete `data/chaos.db` and restart

## Next Steps

- See `IMPLEMENTATION_SUMMARY.md` for full implementation details
- See `HELP.md` for developer documentation
- Check `.spec/phases/` for feature specifications
