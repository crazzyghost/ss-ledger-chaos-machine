# Design Conventions

Apply the relevant section whenever a task involves an external interface or inter-service communication. These conventions keep a system coherent and predictable as it grows.

## REST APIs

- **Resource-oriented URLs**: name by resource, not action — `/users/{id}/orders`, not `/getUserOrders`.
- **Standard HTTP semantics**: use GET, POST, PUT, PATCH, DELETE and the matching status codes correctly (201 for creation, 204 for empty success, 404 vs. 409 vs. 422, etc.).
- **Consistent error format**: define one error envelope and use it everywhere, e.g. `{"error": {"code": "...", "message": "..."}}`. Specify it once in the design and reference it from each endpoint.
- **Versioning**: pick a strategy and state it — path prefix (`/v1/`) or header-based — and stick to it across the API.
- **Document the DTOs**: for each request/response body, specify field types, validation constraints, and nullability. This is what lets the implementer wire up validation without guessing.

## CLI Tools

- Define the command structure, subcommands, and flag conventions up front.
- Specify help text, usage examples, and exit codes (0 for success, distinct non-zero codes for distinct failure classes).
- If a CLI framework is warranted (e.g., picocli, JCommander), recommend it and justify the choice in an ADR rather than hand-rolling argument parsing for anything non-trivial.

## Queue Workers / Event-Driven Services

- **Message schema with explicit versioning** — consumers and producers evolve independently, so version the schema from day one.
- **Idempotency and deduplication** — state how a redelivered message is recognized and safely reprocessed. At-least-once delivery is the norm, so idempotency is not optional.
- **Retry policy, dead-letter handling, and backpressure** — define how many retries, with what backoff, where poison messages go, and how the worker behaves when it can't keep up.
- **Serialization and schema evolution** — choose a format (JSON, Avro, Protobuf) and state the rules for evolving it (additive-only, required-field policy, etc.).

## Microservice Patterns

- **Communication style** — specify sync (REST/gRPC) vs. async (messaging) per interaction, and say why.
- **Resilience patterns** — apply circuit breakers, bulkheads, retries with backoff, and timeouts at the boundaries where a downstream failure could cascade. Name where each applies; don't sprinkle them blindly.
- **Observability** — define health-check endpoints and the metrics, tracing, and structured-logging expectations so the service is operable, not just runnable.
- **Service discovery and configuration** — state how services find each other and how configuration is managed and propagated.

## A note on resilience

Resilience patterns (circuit breakers, bulkheads, backoff, timeouts, backpressure) are design decisions, not afterthoughts. The question to answer in the design is always: *when this dependency is slow or down, what happens to callers?* If the answer is "they hang" or "the failure cascades," the design isn't done. Place the protection at the boundary that contains the blast radius.
