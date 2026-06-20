# ADR 009 - Transactional Outbox for `organization.onboarded`

## Status
Accepted

## Context
Creating an organization through the new onboarding API must **automatically** publish an
`organization.onboarded` Kafka event (idea `001`). The question is *how* to guarantee that the
event and the DB row agree, given the ways this can go wrong:

- **Publish-then-commit** — emit to Kafka inside the request, then the DB transaction rolls back:
  the ledger sees an org that does not exist here (phantom event).
- **Commit-then-publish** (after-commit hook) — the row commits, then the broker is unreachable or
  the app crashes before the send: the org exists but no event is ever emitted (lost event). A
  `@TransactionalEventListener(AFTER_COMMIT)` has exactly this failure window.

The existing `FlowEngine` → `ChaosEventPublisher` path is a *fire-and-forward* publisher built for
the **chaos runner**, where dropped/duplicated/malformed events are the point. It is the wrong
reliability profile for the clean onboarding happy-path.

Notably, the authoritative producer being emulated — `ss-ledger-service` — itself uses a
transactional outbox (`AccountOwnershipOutboxIntegrationTest`,
`OutboxKafkaFailureRetainsRowIntegrationTest`). The user explicitly asked onboarding to "write to
an outbox and publish a Kafka message that matches `organization.onboarded` from the
ss-ledger-service repo."

## Decision
Adopt the **transactional outbox pattern** for onboarding events:

1. In **one DB transaction**, `OrganizationService.onboard(...)` persists the `organization` row
   **and** inserts an `outbox_event` row whose payload is the fully-built
   `EventEnvelope<OrganizationOnboardedEventData>` (serialized JSON), plus topic, partition key,
   and status `PENDING`. Atomic: either both land or neither does.
2. A **polling relay** (`OrganizationOutboxRelay`, `@Scheduled`, running on a virtual-thread
   executor per [ADR-007](007-csv-batch-execution-model.md)) claims `PENDING` rows in order,
   publishes each via the existing Kafka producer infrastructure (`ChaosEventPublisher` /
   `KafkaTemplate`, idempotent producer per [ADR-004](004-event-envelope-and-kafka-publishing.md)),
   and on broker ack marks the row `PUBLISHED`. Send failures leave the row `PENDING` (or `FAILED`
   after N attempts) for retry — the row is retained, never lost.
3. The published envelope is **byte-for-byte the contract** in
   `../ss-ledger-service/bin/publish-organization-onboarded.sh`: `event_type =
   organization.onboarded`, `source = organization-service`, `version = 1.0`, snake_case `data`
   with nested `type {id,name}` and `country {id,name,iso_code,status,modified_date}`, `phone[]`,
   and `metadata {correlation_id, idempotency_key = "organization-onboarded:<event_id>", tenant_id}`.
   This requires **extending `OrganizationOnboardedEventData.Country`** to add `status` and
   `modified_date` (currently `id`/`name`/`iso_code` only).

The **manual chaos flow** for `organization.onboarded` is **retained** — fault injection
(malformed/duplicate/out-of-order/high-volume) stays available through the flow runner and the
existing `OrganizationOnboardedFlowBuilder`. Onboarding adds a *second, clean* producer; it does
not replace the chaos path.

## Consequences
**Positive**
- Exactly the at-least-once delivery guarantee onboarding needs: no phantom events, no silently
  lost events. The DB is the single source of truth and the relay reconciles to it.
- Mirrors the ledger's own onboarding mechanism, reducing contract/behavior surprises.
- Decouples request latency from broker availability — onboarding returns as soon as the row
  commits; delivery happens asynchronously and survives broker outages and app restarts.
- Reuses the idempotent producer and envelope conventions already in place.

**Negative / trade-offs**
- New moving parts: an `outbox_event` table, a scheduled relay, and its claim/visibility logic
  (status + attempt count + ordering). More to test and operate than a direct publish.
- **At-least-once**, not exactly-once: a crash between broker ack and the `PUBLISHED` write
  re-sends. Safe because the ledger consumer is idempotent on `idempotency_key`; the relay must
  preserve a stable `event_id`/`idempotency_key` across retries (no regeneration).
- Eventual (not synchronous) delivery — there is a small, bounded lag between onboarding and the
  event landing, governed by the poll interval.
- Two producers of the same event type must be kept envelope-compatible (covered by a shared
  builder/serializer and a contract test against the ledger sample).
