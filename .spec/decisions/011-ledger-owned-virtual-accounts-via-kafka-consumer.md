# ADR 011 - The ledger owns virtual accounts; the registry is a projection of `ledger.account.created`

## Status
Accepted

## Context
Until now the chaos machine **created and persisted virtual accounts itself**:

- **Phase 002 / Task 003–004** — `POST /api/v0/virtual-accounts` inserted a `virtual_account` row
  directly, optionally *announcing* it to the ledger over Kafka (`organization.onboarded` /
  `organization.va.updated`). The chaos DB was the source of truth for VA existence.
- **Phase 007 / Task 003** — the chart-of-accounts bootstrap issued `POST /api/v0/accounts` to the
  ledger **and then persisted** the returned `accountId` into the chaos `virtual_account` table
  *synchronously, inside the bootstrap transaction*.

Both paths let the chaos DB hold virtual accounts the ledger might not actually have created (the
old "UUID drift" problem Phase 007 partly addressed), and both made the chaos machine the
*authority* on VAs. The idea (`002_countries_va_via_kafka.md`) inverts this explicitly:

> THE LEDGER OWNS Virtual Accounts. … the ledger will publish a `ledger.account.created` topic
> which will be used to create the virtual account. Same for creating ledger accounts from the UI.
> … [the COA bootstrap] should only run the http requests and no longer block to create them
> manually; when the ledger service publishes the `ledger.account.created` messages, then they are
> consumed and persisted.

The authoritative event already exists in `ss-ledger-service`
(`account/events/v1/AccountCreatedEventData`, `account/events/AccountCreatedEventFactory`,
topic pair `ledger.account.created` + `ledger.account.created.dlt`). The ledger publishes it via
its **own transactional outbox** for *every* account it creates — both HTTP-created accounts
(`POST /api/v0/accounts`) and the organization VAs its `OrganizationVirtualAccountProvisioner`
materializes when it consumes `organization.onboarded`.

Crucially, **the chaos machine is today a producer-only application** — there is no
`@KafkaListener`, `ConsumerFactory`, or `spring.kafka.consumer` configuration anywhere. Honouring
this idea means introducing the machine's **first Kafka consumer**.

## Decision
Make the ledger the system of record for virtual accounts and turn the chaos `virtual_account`
table into a **read projection** fed by consuming `ledger.account.created`.

1. **Introduce Kafka consumer infrastructure** (the first in this service): a
   `ConsumerFactory` + `ConcurrentKafkaListenerContainerFactory` using
   `ErrorHandlingDeserializer` around a `JsonDeserializer` (the ledger publishes with
   `spring.json.add.type.headers=false`, so the consumer pins the target type rather than reading
   type headers). A `DefaultErrorHandler` with bounded retry/back-off and a
   `DeadLetterPublishingRecoverer` routes poison records to **`ledger.account.created.dlt`**
   (the DLT the ledger already declares). Consumer group id is configurable
   (`chaos.kafka.consumer.group-id`, default `chaos-machine`).

2. **`LedgerAccountCreatedConsumer`** deserializes the envelope into a chaos-side mirror record
   (`EventEnvelope<LedgerAccountCreatedEventData>`) and **upserts** a `virtual_account` row keyed by
   the ledger `account_id` (the chaos `va_id` becomes the ledger account id). The projection copies
   `account_code`, `account_name`, `account_category`, `currency`, `status`, `organization_id`,
   `account_ownership_type`, and links the matching `account_role` (by `account_code`) when the
   account is a SYSTEM account. `created_via` is recorded as `KAFKA`. Consumption is **idempotent**
   on `account_id` (insert-or-update), so at-least-once redelivery is safe.

3. **Invert VA creation.** `POST /api/v0/virtual-accounts` no longer inserts locally; it becomes a
   **provisioning request** that calls the ledger's `POST /api/v0/accounts` (reusing the Phase 007
   `LedgerAccountProvisioningClient`) and returns `202 Accepted`. The VA appears in the registry
   only once the resulting `ledger.account.created` event is consumed. Organization VAs can be
   requested with **any currency**. The old `VirtualAccountAnnouncer` "announce to ledger" path is
   retired for this purpose (the ledger no longer needs to be told about VAs it owns); the manual
   chaos *fault-injection* flows for `organization.onboarded` / `organization.va.updated` are
   unaffected — they remain in the flow runner for deliberately-bad traffic.

4. **Rework the COA bootstrap to be non-blocking and HTTP-only.** The bootstrap reads its catalog,
   and for each role **checks whether the account `code` already exists in the `virtual_account`
   projection**; if not, it issues `POST /api/v0/accounts` to the ledger and returns. It does
   **not** persist the VA from the HTTP response. The `account_role` row stays `PENDING` until the
   consumer materializes the VA and links `default_va_id`, at which point it flips to `PROVISIONED`.
   The existing `POST /api/v0/chart-of-accounts/bootstrap` endpoint is retained as the **manual COA
   trigger** (surfaced as a button in the UI).

This **supersedes** the VA-ownership and bootstrap-persistence mechanisms of
[Phase 002 / Tasks 003–004](../phases/002-accounts-chart-of-accounts/) and
[Phase 007 / Task 003](../phases/007-chart-of-accounts-http-bootstrap/003-bootstrap-orchestration-and-persistence.md).
Those task files are annotated to point here.

## Consequences
**Positive**
- One source of truth. The chaos registry can no longer disagree with the ledger about which VAs
  exist or what ids they carry — the ids *are* the ledger's, delivered by the ledger.
- The same code path materializes **both** bootstrap SYSTEM accounts and onboarding-driven ORG VAs:
  publish/onboard → ledger creates → `ledger.account.created` → chaos projects. Less special-casing.
- Bootstrap no longer blocks on synchronous persistence; a slow/again-unreachable ledger degrades
  gracefully (HTTP request retried; VA lands whenever the event arrives).
- Establishes reusable consumer infrastructure (deserialization, retry, DLT) the machine can extend
  to other ledger outbound events (`ledger.account.state.changed`, balance updates) later.

**Negative / trade-offs**
- **New moving part: a Kafka consumer**, with its own failure modes (deserialization errors, DLT,
  consumer lag, rebalance) that a producer-only service did not have. Requires consumer config,
  error handling, and integration tests (Testcontainers Kafka).
- **Eventual consistency.** `POST /api/v0/virtual-accounts` and the bootstrap become asynchronous:
  the VA is not visible the instant the call returns. The UI must reflect a "requested/pending"
  state and reconcile when the projection updates. Acceptance criteria and the frontend account for
  this.
- **At-least-once projection.** A redelivered `ledger.account.created` must be a no-op update, not a
  duplicate row — enforced by upsert-on-`account_id` and a unique key.
- **Dependency on the ledger actually publishing.** If the ledger's account outbox is disabled or
  its DLT misconfigured, VAs never materialize. This is acceptable because the ledger is the owner
  by design; surfaced via consumer-lag metrics and the bootstrap's per-role `PENDING` status.
- Some now-dead code (direct VA insert, `VirtualAccountAnnouncer` announce path) is removed or
  reduced, touching Phase 002/004 tests.
