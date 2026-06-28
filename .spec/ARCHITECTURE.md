# ss-ledger-chaos-machine — Architecture

> Single source of truth for the overall design. Each phase has its own `DESIGN.md`
> (linked below) and per-task specs. Update this file whenever a phase is added or revised.

## 1. Purpose

The **ledger chaos machine** is a controlled resilience-testing harness for
[`ss-ledger-service`](../ss-ledger-service). It lets an operator drive the ledger through
its Kafka event surface from a UI — issuing well-formed transaction flows **or**
deliberately malformed/duplicated/out-of-order/high-volume traffic — and observe how the
ledger copes (idempotency, validation, DLT routing, backpressure, balance integrity).

It is **not** a ledger. It owns no journals or balances. It is a *driver* + *gateway*:

- **Driver** — formulates and publishes the exact Kafka events the ledger consumes,
  individually or from CSV, with optional chaos injection.
- **Gateway** — the React UI talks only to this backend, which proxies login to the
  **AUTH SERVICE** and proxies account/transaction **reads** from the ledger.

## 2. Targets & Conventions

| Concern | Decision | Source |
|---|---|---|
| Backend language | **Java 25** | mirrors ledger ([ADR-001](decisions/001-target-java-25-and-spring-boot-4.md)) |
| Backend framework | **Spring Boot 4.0.6**, Gradle, group `com.softspark` | mirrors ledger |
| Backend base package | `com.softspark.chaos` | new |
| Persistence | **SQLite** via JPA + Hibernate community dialect + Flyway | manifest ([ADR-002](decisions/002-sqlite-persistence-with-jpa-and-flyway.md)) |
| API style | REST under `/api/v0`, records as DTOs, `record-builder` (no Lombok) | mirrors ledger |
| API docs | springdoc OpenAPI + Swagger UI with a **`bearerAuth`** HTTP security scheme | mirrors ledger |
| Chart of accounts | **Provisioned in the ledger over HTTP**; VA ids are ledger-assigned (not config) | [Phase 007](phases/007-chart-of-accounts-http-bootstrap/DESIGN.md) |
| **Virtual accounts** | **The ledger owns VAs**; the chaos `virtual_account` table is a **projection** of the `ledger.account.created` Kafka event (chaos's first consumer). VA creation + CoA bootstrap issue HTTP to the ledger and never persist VAs directly | [Phase 009](phases/009-ledger-owned-virtual-accounts/DESIGN.md) ([ADR-011](decisions/011-ledger-owned-virtual-accounts-via-kafka-consumer.md)) |
| Org reference data | **Countries & org types** are first-class tables (UUID v4 ids); orgs FK them. **Currencies** are a managed table (UUID id + ISO-4217 code); `country` has a `primary_currency`; **supported countries** are a separate curated table driving the onboarding form | [Phase 008](phases/008-organization-onboarding/DESIGN.md) + [Phase 010](phases/010-currencies-and-supported-countries/DESIGN.md) ([ADR-008](decisions/008-organization-onboarding-domain-model.md), [ADR-010](decisions/010-uuid-v4-ids-for-organization-domain.md), [ADR-012](decisions/012-currency-and-supported-country-reference-model.md)) |
| Reference-data seeding | **Countries + currencies pre-seeded at startup from the external [restcountries.com](https://restcountries.com/) API** (async, seed-if-empty, degrade-on-failure); manual entries + `POST /countries/refresh` | [Phase 010](phases/010-currencies-and-supported-countries/DESIGN.md) ([ADR-013](decisions/013-seed-countries-and-currencies-from-restcountries-api.md)) |
| Org onboarding | Creating an org writes a **transactional outbox** row → relay publishes `organization.onboarded` (now incl. top-level `currency {id, code}` from the country's primary currency) | [Phase 008](phases/008-organization-onboarding/DESIGN.md) + [Phase 010](phases/010-currencies-and-supported-countries/DESIGN.md) ([ADR-009](decisions/009-transactional-outbox-for-organization-onboarded.md), [ADR-012](decisions/012-currency-and-supported-country-reference-model.md)) |
| Topology | Backend is the **single gateway** for the UI | user-confirmed ([ADR-003](decisions/003-backend-as-single-api-gateway.md)) |
| Eventing (out) | `EventEnvelope<T>` snake_case + `KafkaTemplate` producer | mirrors ledger ([ADR-004](decisions/004-event-envelope-and-kafka-publishing.md)) |
| Eventing (in) | **Multi-event Kafka consumer** of the ledger's outbound surface. One method-typed container factory (`ByteArrayJsonMessageConverter`; target type = each listener's `EventEnvelope<T>`) + retry/back-off + topic-derived `DeadLetterPublishingRecoverer`. Consumers: `ledger.account.created` (VA projection), `ledger.transaction.failed` (failure projection), `ledger.balance.updated` (balance-history projection), `ledger.reservation.created`/`.released` (reservation lifecycle projection) | [Phase 009](phases/009-ledger-owned-virtual-accounts/DESIGN.md) ([ADR-011](decisions/011-ledger-owned-virtual-accounts-via-kafka-consumer.md)); generalized in [Phase 017](phases/017-ledger-transaction-failure-events/DESIGN.md) ([ADR-024](decisions/024-multi-event-ledger-outbound-consumer.md)); extended in [Phase 018](phases/018-balance-history/DESIGN.md) ([ADR-027](decisions/027-balance-history-projection-from-ledger-balance-updated.md)) + [Phase 019](phases/019-reservation-lifecycle-tracking/DESIGN.md) ([ADR-028](decisions/028-reservation-lifecycle-projection.md)) |
| **Ledger-failure correlation** | The chaos machine consumes `ledger.transaction.failed` into a `transaction_failure` projection and correlates each failure to its publish by **`transaction_request_id`** (the failure's `metadata.correlation_id` is the ledger's recording id, *not* the chaos correlation id). The publish side labels/persists/echoes that id; run page **polls→toasts**, the "Sent" tab shows a **ledger Outcome** column | [Phase 017](phases/017-ledger-transaction-failure-events/DESIGN.md) ([ADR-025](decisions/025-transaction-failure-projection-and-request-id-correlation.md), [ADR-026](decisions/026-run-page-failure-surfacing-via-bounded-polling.md)) |
| Auth | Token introspection via external **AUTH SERVICE** (no local JWT signing) | mirrors ledger ([ADR-006](decisions/006-auth-via-external-auth-service.md)) |
| Frontend | **React 19 + Vite 6 + react-router 7 + react-query 5 + Tailwind + shadcn/ui** | mirrors swift-admin ([ADR-005](decisions/005-react-vite-shadcn-frontend.md)) |
| Batch execution | Bounded async workers on **virtual threads** | [ADR-007](decisions/007-csv-batch-execution-model.md) |
| **Lifecycle flows** | Collection (single) + Settlement/Disbursement **lifecycles** (`initiated`→`completed`\|`failed`); operator outcome **Succeed/Fail** = interactive 2-step wizard, **Random** = unattended server runner (N-Times-capable, run-tracked). `transaction_id` is the carry-over linkage | [Phase 014](phases/014-collection-settlement-disbursement-flows/DESIGN.md) ([ADR-017](decisions/017-lifecycle-transaction-flows-and-outcome-orchestration.md)) |
| **Disbursement reservation** | `reservation_id` sourced via a **ledger reservations read-proxy poll** (ledger keys off `transaction_id` and ignores the inbound `reservation_id`); timeout → manual/placeholder | [Phase 014](phases/014-collection-settlement-disbursement-flows/DESIGN.md) ([ADR-018](decisions/018-reservation-id-via-ledger-read-proxy-poll.md)) |
| **Dynamic fees** | Shared `TransactionFeeLine` (incl. `fee_code`) + typed `PublishFlowRequest.fees[]`; `FEE_LIST`/`COUNTRY`/`ULID` descriptor kinds | [Phase 014](phases/014-collection-settlement-disbursement-flows/DESIGN.md) ([ADR-019](decisions/019-dynamic-fee-lines-and-catalog-descriptor-extensions.md)) |
| **Balance display** | VA detail shows all four buckets (Total/Available/Reserved/Pending) + a **point-in-time "Balance As Of"** view (ledger `?asOf` `LocalDateTime`, zoneless); VA list views show a **total-balance column** fed by one **batch-balance** read-proxy call per page. Thin read-proxy extensions; corrects the camelCase `LedgerBalanceDto` drift | [Phase 015](phases/015-virtual-account-balance-display/DESIGN.md) ([ADR-020](decisions/020-as-of-balance-via-ledger-read-proxy.md), [ADR-021](decisions/021-batch-balance-read-proxy-for-list-column.md)) |
| **Batch disbursement** | A **fan-out lifecycle** (one BATCH reservation → N items, each a request → completed\|failed) driven from Single Flow Run, **distinct from CSV**. Four new flow types over `disbursement.batch.initiated` (`operation` discriminator) + `disbursement.batch.item.{completed,failed}`; **Manual** = client wizard cycling N items (per-item pass/fail), **Automatic** = unattended server runner (even split + outcome policy all/none/K/random) reusing the batch infra (`RunKind.BATCH_DISBURSEMENT`). `reservation_id` + live progress via a `disbursement-batches/{batchId}` read-proxy | [Phase 016](phases/016-batch-disbursement/DESIGN.md) ([ADR-022](decisions/022-batch-disbursement-fan-out-flow-and-dual-mode-orchestration.md), [ADR-023](decisions/023-batch-reservation-id-and-progress-via-batch-summary-read-proxy.md)) |

## 3. C4 — System Context & Containers

```mermaid
flowchart TB
    operator([Operator / QA Engineer])

    subgraph cm[ss-ledger-chaos-machine]
        ui["Chaos Admin UI<br/>(React + Vite, follows swift-admin)"]
        be["Chaos Backend<br/>(Spring Boot 4, Java 25)"]
        db[("SQLite<br/>chart of accounts, VA registry,<br/>publish history, batch runs")]
        ui -->|"REST /api/v0 (Bearer)"| be
        be --> db
    end

    auth["AUTH SERVICE<br/>(token issue + introspection)"]
    kafka{{"Kafka<br/>ledger inbound topics + ledger.account.created"}}
    ledger["ss-ledger-service<br/>(consumes events, owns journals/balances + virtual accounts)"]
    restcountries["restcountries.com<br/>(country + currency reference data)"]

    operator --> ui
    be -->|"login + token verify"| auth
    be -->|"seed countries + currencies at startup"| restcountries
    be -->|"publish flow events"| kafka
    be -->|"create accounts / VAs (POST /accounts)"| ledger
    be -->|"read accounts / transactions (RestClient)"| ledger
    kafka -->|"ledger.account.created → VA projection"| be
    kafka --> ledger
    ledger -->|"ledger.account.created"| kafka
```

## 4. Backend module map (`com.softspark.chaos`)

Feature-first with layer subpackages (mirrors ledger):

```
com.softspark.chaos
├── Application
├── config            # security, openapi, async/virtual-threads, web
├── advice            # GlobalExceptionHandler, ApiError, ErrorDescription
├── base              # shared records, pagination, ids (ULID), clock
│                     # Phase 018: + BigDecimalStringConverter (precision-safe BigDecimal↔TEXT)
├── kafka             # out: EventEnvelope, EventMetadata, ProducerConfiguration, TopicCatalog, ChaosEventPublisher
│                     # in (Phase 009): ConsumerConfiguration, ConsumerProperties (ErrorHandlingDeserializer + DLT)
│                     #   Phase 017: ConsumerConfiguration generalized to method-typed multi-event
│                     #   (ByteArrayJsonMessageConverter; one factory for all ledger-outbound listeners);
│                     #   TopicCatalog + ledger.transaction.failed (+ derived .dlt)
├── account           # chart of accounts + virtual account registry (a *projection* of ledger.account.created)
│   ├── controller / dto / service / repository / model / enumeration / bootstrap / consumer
│   ├── bootstrap     # Phase 007/009: catalog config, ledger HTTP provisioning, non-blocking runner
│   └── consumer      # Phase 009: LedgerAccountCreatedConsumer + mirror payload → VA projection
├── organization      # Phase 008/010: countries + org types + currencies + supported countries, org onboarding
│   ├── controller / dto / service / repository / model / enumeration  # incl. Currency, SupportedCountry
│   ├── seed          # Phase 010: RestCountriesClient + ReferenceDataSeeder (startup seed from restcountries.com)
│   └── outbox        # OutboxEvent entity + polling relay → organization.onboarded (incl. currency {id,code})
├── flow              # transaction flow engine
│   ├── controller / dto / service / model(payloads v1) / chaos / registry
│   │                 # Phase 014: + DISBURSEMENT_INITIATED/_FAILED builders, corrected
│   │                 #   Collection/Disbursement v1 models, shared TransactionFeeLine (fee_code),
│   │                 #   PublishFlowRequest.fees[], FlowLifecycle/CarryOver catalog metadata,
│   │                 #   FieldKind.FEE_LIST/COUNTRY + AutogenRule.ULID, LifecycleRunner
│   │                 #   (RANDOM unattended → /flows/{lifecycleType}/random-lifecycle)
│   │                 # Phase 016: + 4 batch-disbursement flow types/builders/v1 models
│   │                 #   (disbursement.batch.initiated {operation} + .item.completed/.failed),
│   │                 #   BatchDisbursementGroup catalog grouping + carry-over, FieldKind.INTEGER,
│   │                 #   BatchOutcomePolicy + BatchSplit, BatchDisbursementRunner/RunService
│   │                 #   (automatic → /flows/disbursement-batch/run)
│   │                 # Phase 017: FlowBuilder.transactionRequestIdField() labels the per-flow
│   │                 #   request-id field (= ledger's transactionRequestId); FlowEngine echoes it
│   │                 #   into FlowResult.transactionRequestId / NTimesSyncResult.transactionRequestIds
│   └── chaos         # duplicate/outOfOrder/malformed/unbalanced/burst/delay
│                     #   + Phase 013: NTimesOptions (Pacing/ExecutionMode) + NTimesExpander
│                     #   (1 request → N distinct requests) for the /flows/{type}/n-times route
├── batch             # CSV ingest + batch run execution
│   ├── controller / dto / service / model / repository / csv
│                     # Phase 013: batch_run gains a `kind` (CSV | N_TIMES) discriminator so the
│                     #   reused runner/tables also track async N-Times runs (pacing/mode columns)
│                     # Phase 014: + RunKind.LIFECYCLE (RANDOM lifecycle runner)
│                     # Phase 016: + RunKind.BATCH_DISBURSEMENT (automatic batch runner; 1 row =
│                     #   1 item request+terminal unit) + nullable external_batch_id/reservation_id
├── history           # publish records + query API
│   ├── controller / dto / service / model / repository
│                     # Phase 017: publish_record gains transaction_request_id (labelled per flow,
│                     #   indexed) so failures join back to publishes; PublishRecordResponse exposes it
├── transaction       # Phase 017: ledger.transaction.failed projection + query API
│   ├── consumer      # LedgerTransactionFailedConsumer + LedgerTransactionFailedEventData (mirror)
│   ├── model / repository / service / controller / dto
│                     #   transaction_failure projection (idempotent upsert by event_id) +
│                     #   GET /api/v0/transaction-failures (by request id / batch / type / time)
├── balance           # Phase 018: ledger.balance.updated projection + query API
│   ├── consumer      # LedgerBalanceUpdatedConsumer + LedgerBalanceUpdatedEventData (mirror)
│   ├── model / repository / service / controller / dto
│                     #   balance_history projection (per account; idempotent upsert by event_id;
│                     #   currency backfilled from VA registry) +
│                     #   GET /api/v0/virtual-accounts/{vaId}/balance-history
├── reservation       # Phase 019: ledger.reservation.created/.released projection + query API
│   ├── consumer      # LedgerReservationConsumer (both topics) + LedgerReservationLifecycleEventData
│   ├── model / repository / service / controller / dto
│                     #   reservation projection (stateful, 1 row/reservation_id; monotonic status
│                     #   ACTIVE→PARTIALLY_RESOLVED→CAPTURED|RELEASED|EXPIRED; batch-aware) +
│                     #   GET /api/v0/virtual-accounts/{vaId}/reservations + flat /reservations
│                     #   (ReservationStateResponse — distinct from ledgerproxy ReservationResponse)
├── dlq               # Phase 020: ledger inbound DLT (ledger.<flow>.dlt) projection + query API
│   ├── consumer      # LedgerDeadLetterConsumer (tolerant, own DLQ_CONTAINER_FACTORY, NO recoverer/re-DLT)
│   │                 #   + LedgerDeadLetterRecord (mirror of ledger DeadLetterTopicRecord)
│   ├── model / repository / service / controller / dto
│                     #   dlq projection (one table, domain-tagged; dedup by topic/partition/offset;
│                     #   best-effort transaction_id/type from original payload) +
│                     #   GET /api/v0/dlq (domain/transactionId/transactionType) + /dlq/{id}
├── auth              # login proxy + AccessTokenFilter + TokenVerifier (AUTH SERVICE)
└── ledgerproxy       # RestClient read-through to ss-ledger-service (accounts, transactions,
                      #   + Phase 012: reporting/trial-balance via LedgerReadController + LedgerClient
                      #   + Phase 014: accounts/{id}/reservations read-proxy + ReservationLookup
                      #     (poll-until-present-or-timeout) for disbursement reservation_id)
                      #   + Phase 015: accounts/{id}/balance gains optional ?asOf (PIT, LocalDateTime);
                      #     new /balances batch read-proxy (BatchBalanceItemDto); LedgerBalanceDto
                      #     aligned to the ledger's camelCase contract (balanceAsOf, lastEntrySequence)
                      #   + Phase 016: disbursement-batches/{batchId} read-proxy
                      #     (DisbursementBatchSummaryDto: reservation_id + status + counters) +
                      #     BatchReservationLookup (poll by batch_id) for batch reservation_id/progress
```

## 5. Frontend module map (`src/`, follows swift-admin)

```
src
├── app               # router.tsx (createBrowserRouter), error boundary
├── main.tsx          # QueryClientProvider, RouterProvider
├── lib               # api.ts (fetch + Bearer + ApiError), env.ts (appConfig), auth.ts
├── components
│   ├── layout        # app-shell.tsx (sidebar nav), page primitives
│   └── ui            # shadcn primitives (button, card, dialog, select, table, input…)
└── features
    ├── auth          # login-page, session-provider, protected-route
    ├── chart-of-accounts
    ├── virtual-accounts   # list, create, detail (+ per-VA transactions)
    │                      #   Phase 015: detail sub-header available balance + Ledger Balance panel
    │                      #     (4 buckets, moved above Chaos Registry Details) + Balance-As-Of picker;
    │                      #     list views gain a total-balance column (batch balances, one call/page)
    │                      #   Phase 018: detail gains a **Balance** tab (balance-history-tab.tsx) listing
    │                      #     the per-account ledger.balance.updated event log (offset-paginated)
    │                      #   Phase 019: detail gains a **Reservations** tab (reservations-tab.tsx) listing
    │                      #     the account's reservation lifecycle states (type/status/amount/batch)
    ├── transactions       # search by VA id + filters
    │                      #   Phase 017: "Sent" tab gains a ledger **Outcome** column (published-to-Kafka
    │                      #     vs ledger-accepted/rejected) via one batch /transaction-failures lookup per page
    ├── trial-balance      # Phase 012: read-only trial-balance report (period + currency filters, totals, per-account table)
    └── chaos              # Single Flow Run (radio + catalog-driven form + chaos widget) + CSV upload + run results
                           #   Phase 011: transaction-type-form, va-picker, chaos-options-panel (two-column)
                           #   Phase 014: fee-list-field, country-select, lifecycle-wizard (2-step,
                           #     outcome-selector, both-forms step 2), reservation-field (poll→manual),
                           #     RANDOM count → random-lifecycle → run-results handoff
                           #   Phase 017: use-transaction-failure-watch (bounded scoped poll by the emitted
                           #     transactionRequestId(s)) → sonner danger toast + result-card "Failed at ledger"
                           #     (global <Toaster/> mounted in components/layout/app-shell)
                           #   Phase 018: use-balance-update-watch (bounded poll by involved account ids +
                           #     time watermark) → sonner info toast "Balance updated on {account}" (reuses Toaster)
                           #   Phase 019: use-reservation-watch (bounded poll by transactionRef/batch_id) →
                           #     sonner toasts on reservation created + released/expired/captured (lifecycle +
                           #     batch wizards); ADR-018/023 read-proxy reservation_id sourcing unchanged
    └── dlq                # Phase 020: "Dead Letter Queue" (Operate nav, route /chaos/dlq[/:id])
                           #   dead-letter-queue-page (filterable list: domain/txn id/txn type) +
                           #   dead-letter-queue-detail-page (tabbed: Overview + Message via JsonPanel)
```

## 6. The ledger flows (event surface the chaos machine drives)

All published as `EventEnvelope<T>` (snake_case) to the topic named by `event_type`.
Full schemas live in [Phase 003 / task 002](phases/003-transaction-flow-engine/002-single-transaction-publishing-api.md).

As of [Phase 008](phases/008-organization-onboarding/DESIGN.md), `organization.onboarded` has **two
producers**: the manual chaos flow runner (for fault injection — malformed/duplicate/out-of-order)
*and* the organization onboarding API, which emits a clean event via the transactional outbox. Both
publish the identical `EventEnvelope<OrganizationOnboardedEventData>` shape.

| Flow | Topic / `event_type` | `source` |
|---|---|---|
| Organization onboarded | `organization.onboarded` | organization-service |
| VA updated | `organization.va.updated` | organization-service |
| Top-up confirmed | `organization.topup.confirmed` | payments-service |
| Inter-VA transfer | `organization.transfer.requested` | transfers-service |
| Treasury prefund | `organization.treasury.prefund.completed` | treasury-service |
| Treasury sweep | `organization.treasury.sweep.completed` | treasury-service |
| Treasury transfer | `organization.treasury.transfer.completed` | treasury-service |
| Settlement initiated | `organization.va.settlement.initiated` | settlements-service |
| Settlement completed | `organization.va.settlement.completed` | settlements-service |
| Settlement failed | `organization.va.settlement.failed` | settlements-service |
| Collection completed | `collection.completed` | payments-service |
| **Disbursement initiated / completed / failed** | `disbursement.{initiated,completed,failed}` ² | payment-service |
| **Batch disbursement** (reservation + per-item request/completed/failed) | `disbursement.batch.initiated` (`operation` ∈ {`BATCH_RESERVATION_REQUEST`, `BATCH_ITEM_REQUEST`}) + `disbursement.batch.item.{completed,failed}` ³ | payment-service |

² **Resolved in [Phase 014](phases/014-collection-settlement-disbursement-flows/DESIGN.md)
([ADR-019](decisions/019-dynamic-fee-lines-and-catalog-descriptor-extensions.md)):** the
disbursement contract is now **verified against `ss-ledger-service` source** (+
`bin/kafka-payload-samples.md`). It is a **lifecycle** — `disbursement.initiated` →
`disbursement.completed` | `disbursement.failed` (`source: payment-service`) — driven from the
Single Flow Run. The prior chaos `disbursement.completed` model (hand-guessed:
`disbursement_request_id`/`recipient_account_number`/…) was **wrong** and is rewritten to the
real fields (`transaction_id`, `reservation_id`, `disbursement_subtype`, `provider_reference_id`,
`fees[]`, …). The ledger links the lifecycle by `transaction_id` and **ignores** the inbound
`reservation_id` (see §10.4). Collection (`collection.completed`) is similarly corrected (real
`transaction_id`/`provider_id`/`fees[]` with `fee_code`/`commission_split_id`). Batch
disbursement/settlement (`BATCH_*`) remain out of scope here (CSV batch runner).

³ **Added in [Phase 016](phases/016-batch-disbursement/DESIGN.md)
([ADR-022](decisions/022-batch-disbursement-fan-out-flow-and-dual-mode-orchestration.md),
[ADR-023](decisions/023-batch-reservation-id-and-progress-via-batch-summary-read-proxy.md)):**
batch disbursement is a **fan-out lifecycle** — one `BATCH_RESERVATION_REQUEST` (holds
`total_amount` against the source ORG VA, declares `item_count = N`) → per item a
`BATCH_ITEM_REQUEST` (inert at the ledger) → `disbursement.batch.item.completed` (partial
capture + journal) or `.failed` (partial release, no journal); the ledger derives batch
status from completed/failed counters. Driven from Single Flow Run in **Manual** (client
wizard, per-item pass/fail) or **Automatic** (server runner, even split + outcome policy)
mode. Contracts verified against `ss-ledger-service` source +
`bin/kafka-payload-samples.md`. Batch **settlement** remains out of scope.

## 7. Chart of accounts (system accounts provisioned in the ledger)

Friendly **account roles** → a ledger SYSTEM account, referenced when filling the
`source_va_id` / `destination_va_id` / fee slots of flows. On startup the chaos machine reads
the role/code catalog from YAML and **provisions each account in the ledger over HTTP**; the
ledger-assigned `accountId` becomes the role's VA id (stored in the chaos DB). Account **codes
are unique**. Editable via API. (See
[Phase 007](phases/007-chart-of-accounts-http-bootstrap/DESIGN.md), which supersedes the
config-seeded approach of Phase 002 / task 001.)

| Role | Account code (unique) | Category |
|---|---|---|
| `SETTLEMENT_ACCOUNT` | `ASSET.BANK.SETTLEMENT.0000000000001.GHS` | ASSET |
| `PLATFORM_FLOAT` | `ASSET.PLATFORM.FLOAT` | ASSET |
| `PLATFORM_FLOAT_MTN` | `ASSET.PLATFORM.FLOAT.MTN` ¹ | ASSET |
| `PLATFORM_FLOAT_TELECEL` | `ASSET.PLATFORM.FLOAT.TELECEL` | ASSET |
| `PLATFORM_FEE` | `REVENUE.PLATFORM.FEE` | REVENUE |
| `PROVIDER_FEE` | `REVENUE.PROVIDER.FEE` ¹ | REVENUE |

¹ Corrected from the MANIFEST (duplicate / missing codes). See open questions §10. VA UUIDs are
**not** in config — they come from the ledger at provisioning time.

## 8. Phases

| # | Phase | Outcome |
|---|---|---|
| 001 | [Foundations](phases/001-foundations/DESIGN.md) | Build, SQLite persistence, web conventions, Kafka envelope + producer |
| 002 | [Accounts & Chart of Accounts](phases/002-accounts-chart-of-accounts/DESIGN.md) | CoA config, VA registry via API & Kafka |
| 007 | [Chart of Accounts HTTP Bootstrap](phases/007-chart-of-accounts-http-bootstrap/DESIGN.md) | Provision SYSTEM accounts in the ledger over HTTP; store ledger-assigned VA ids (supersedes 002/task 001 seeding). *Formerly numbered 025.* |
| 003 | [Transaction Flow Engine](phases/003-transaction-flow-engine/DESIGN.md) | Single + CSV publishing, chaos injection, publish history |
| 004 | [Gateway: Auth & Ledger Proxy](phases/004-gateway-auth-ledger-proxy/DESIGN.md) | Login proxy + resilient ledger read proxy |
| 005 | [Frontend Admin](phases/005-frontend-admin/DESIGN.md) | React/Vite UI: auth, CoA, VAs, transactions, chaos runner |
| 006 | [Testing & Verification](phases/006-testing-and-verification/DESIGN.md) | Backend unit + integration, frontend, and e2e chaos verification |
| 008 | [Organization Onboarding](phases/008-organization-onboarding/DESIGN.md) | Countries + org types master data, organization onboarding API, transactional outbox publishing `organization.onboarded` |
| 009 | [Ledger-Owned Virtual Accounts](phases/009-ledger-owned-virtual-accounts/DESIGN.md) | The ledger owns VAs; chaos's **first Kafka consumer** projects `ledger.account.created` into the VA registry; VA-create API + CoA bootstrap become HTTP-only/non-blocking; manual CoA trigger in UI (supersedes VA ownership of 002/004 + sync persistence of 007) |
| 010 | [Currencies & Supported Countries](phases/010-currencies-and-supported-countries/DESIGN.md) | Managed `currency` table (seeded + manual), `country.primary_currency`, separate `supported_country` table driving the onboarding form, `organization.onboarded` carries `currency {id, code}` |
| 011 | [Single Flow Run Redesign](phases/011-single-flow-run-redesign/DESIGN.md) | Reworks the chaos Single-Flow runner into **Single Flow Run**: nav rename, **radio** of 5 transaction types (drops onboarded + va-updated; settlement/collection/disbursement deferred), two-column layout (form left / chaos right), field-descriptor catalog (`fields[]` + `runnerVisible`), required-shown/advanced-collapsed form, autogen UUID request ids, account-kind VA pickers, **client-side** org/currency/tenant inference ([ADR-014](decisions/014-flow-catalog-field-descriptors-and-client-side-inference.md)) |
| 012 | [Trial Balance Reporting](phases/012-trial-balance-reporting/DESIGN.md) | Adds a **Trial Balance** nav item + read-only report page (period + currency filters, debit/credit totals, balanced indicator, per-account breakdown), backed by a thin read-proxy of the ledger's `GET /api/v0/reporting/trial-balance` exposed as `GET /api/v0/ledger/reporting/trial-balance` — reusing the existing ledger proxy machinery; no new tables/Kafka ([ADR-015](decisions/015-trial-balance-via-ledger-read-proxy.md)) |
| 013 | [N-Times Chaos Strategy](phases/013-n-times-chaos-strategy/DESIGN.md) | Adds an **N Times** chaos strategy: run a flow N times against the **same** source/destination accounts as N **distinct** transactions (fresh event id → idempotency key + fresh payload `*_request_id` per iteration, shared correlation id) — *distinct from* the duplicate-keyed **Burst**. Three pacings (BURST/LINEAR/RANDOM) and two execution modes: **SYNC** (in-line, sequential, capped) and **ASYNC** (run-tracked, reusing the Phase 003 batch runner; BURST fans out concurrently). Dedicated `POST /api/v0/flows/{flowType}/n-times`; reuses the `autogen` descriptors and the batch run tables behind a `kind` discriminator ([ADR-016](decisions/016-n-times-distinct-transaction-chaos-strategy.md)) |
| 014 | [Collection, Settlement & Disbursement Flows](phases/014-collection-settlement-disbursement-flows/DESIGN.md) | Adds the three deferred transaction types: **Collection** (single, dynamic fee form), **Settlement** & **Disbursement** **lifecycles** (`initiated`→`completed`\|`failed`) with operator outcome (Succeed/Fail = interactive 2-step wizard; Random = unattended, N-Times-capable, run-tracked). **Corrects the Collection/Disbursement inbound models to the authoritative ledger contract** + adds `DISBURSEMENT_INITIATED`/`_FAILED`; sources `reservation_id` via a ledger reservations read-proxy poll; shared `TransactionFeeLine` + typed `fees[]` ([ADR-017](decisions/017-lifecycle-transaction-flows-and-outcome-orchestration.md), [ADR-018](decisions/018-reservation-id-via-ledger-read-proxy-poll.md), [ADR-019](decisions/019-dynamic-fee-lines-and-catalog-descriptor-extensions.md)) |
| 015 | [Virtual Account Balance Display](phases/015-virtual-account-balance-display/DESIGN.md) | Surfaces ledger balances in the VA views: detail **sub-header available balance** (visible across tabs), **Ledger Balance** panel moved above "Chaos Registry Details" showing **all four buckets** (Total/Available/Reserved/Pending), and a **"Balance As Of"** point-in-time picker (ledger `?asOf`, reconstructed from journal witnesses). List views gain a **total-balance column** between Currency and Owner, fed by a new **batch-balance** read-proxy (`GET /ledger/balances`, one call/page), with **unified `createdAt`-DESC ordering** across both list tabs. Thin read-proxy extensions (no tables/Kafka); also **fixes the camelCase `LedgerBalanceDto` drift** that left `accountId`/timestamp null ([ADR-020](decisions/020-as-of-balance-via-ledger-read-proxy.md), [ADR-021](decisions/021-batch-balance-read-proxy-for-list-column.md)) |
| 016 | [Batch Disbursement](phases/016-batch-disbursement/DESIGN.md) | Adds **Batch Disbursement** to Single Flow Run — a real ledger **fan-out lifecycle** (one BATCH reservation → N items, each a request → completed\|failed), **distinct from CSV**. Four new flow types over `disbursement.batch.initiated` (`operation` discriminator) + `disbursement.batch.item.{completed,failed}` with **structured idempotency keys**; **Manual** mode = a client wizard cycling N item forms (per-item pass/fail + chaos), **Automatic** mode = an unattended server runner (even split + outcome policy all/none/K/random) reusing the batch infra behind `RunKind.BATCH_DISBURSEMENT`. `reservation_id` + live batch status/counters via a `GET /ledger/disbursement-batches/{batchId}` read-proxy (keyed by `batch_id`); one additive Flyway migration ([ADR-022](decisions/022-batch-disbursement-fan-out-flow-and-dual-mode-orchestration.md), [ADR-023](decisions/023-batch-reservation-id-and-progress-via-batch-summary-read-proxy.md)) |
| 017 | [Ledger Transaction-Failure Events & Correlation](phases/017-ledger-transaction-failure-events/DESIGN.md) | Adds the chaos machine's **second inbound consumer** — `ledger.transaction.failed` → a `transaction_failure` projection — and **correlates** each failure to its publish by `transaction_request_id` (the only reliable key: the failure's `correlation_id` is the ledger's recording id, not the chaos one). **Generalizes the Phase 009 consumer** to method-typed, multi-event deserialization (one factory for the whole ledger-outbound surface). Run page **polls→toasts** on a just-published transaction's ledger rejection; the "Sent" tab gains a **ledger Outcome** column. Publish side labels/persists/echoes the request id; two additive Flyway migrations (`V12`/`V13`); **Part 1 of 4** ("testing ledger Kafka events") ([ADR-024](decisions/024-multi-event-ledger-outbound-consumer.md), [ADR-025](decisions/025-transaction-failure-projection-and-request-id-correlation.md), [ADR-026](decisions/026-run-page-failure-surfacing-via-bounded-polling.md)) |
| 018 | [Balance History](phases/018-balance-history/DESIGN.md) | Adds the **third inbound consumer** — `ledger.balance.updated` → a per-account, event-sourced **`balance_history`** projection (Flyway `V14`) — plus `GET /api/v0/virtual-accounts/{vaId}/balance-history` (+ a flat/batch variant), a **Balance tab** on the VA detail page, and a **run-page info toast** ("Balance updated on {account}") reusing the Part 1 toaster. Rides the Part 1 generalized consumer (a listener + mirror record). Per-account, **not** transaction-correlatable (`ledger.balance.updated` carries no `transaction_request_id`; `correlation_id` is random) — the run-page toast is therefore heuristically scoped (account + time), with honest copy; complements (does not supersede) Phase 015's live read-through. **Part 2 of 4** ([ADR-027](decisions/027-balance-history-projection-from-ledger-balance-updated.md)) |
| 019 | [Reservation Lifecycle Tracking](phases/019-reservation-lifecycle-tracking/DESIGN.md) | Adds the **fourth inbound consumer** — `ledger.reservation.created` + `.released` → a **stateful, batch-aware `reservation`** projection (Flyway `V15`; `ACTIVE`→`PARTIALLY_RESOLVED`→`CAPTURED`\|`RELEASED`\|`EXPIRED`, monotonic upsert by `reservation_id`) — plus per-VA + flat query endpoints, a **Reservations tab** on the VA detail page, and **create/release toasts** in the settlement & disbursement flows. **Precisely** correlatable: the event's `transaction_id` is the inbound `transactionRef` = the chaos request id, so toasts are request-id-scoped (not heuristic). Complements (does not supersede) the ADR-018/023 read-proxy `reservation_id` sourcing. **Part 3 of 4** ([ADR-028](decisions/028-reservation-lifecycle-projection.md)) |
| 020 | [Dead Letter Queue Views](phases/020-dead-letter-queue-views/DESIGN.md) | **Final part (4 of 4).** A **tolerant DLT consumer** (no re-dead-lettering) ingests the ledger's **inbound** DLTs (`ledger.<flow-topic>.dlt`, 17 topics — the chaos machine's deliberately-bad traffic the ledger rejected) into a **single domain-tagged `dlq` table** (Flyway `V16`; rich `DeadLetterTopicRecord` format → error class/reason + retry count + original payload; dedup by topic/partition/offset; best-effort txn-id/type extraction). Adds `GET /api/v0/dlq` (filters: domain / transaction id / transaction type) + `/{id}`, and a **"Dead Letter Queue"** nav item under *Operate* → list → tabbed detail (Overview + Message). Chaos-own consumer DLTs are out of scope (different format), foldable in later behind a `source` discriminator ([ADR-029](decisions/029-dead-letter-queue-projection.md)) |

Build order: 001 → 002 → 007 → (003, 004 in parallel) → 005 → 006 → **008** → **(009 ‖ 010)** → **011** → **012** → **013** → **014** → **015** → **016** → **017** → **018** → **019** → **020**.
Phase 007 (formerly `025`, a "phase 2.5" label) slots logically between 002 and 003; 006 verifies
phases 001–007. Phases 009 and 010 are the latest increment (idea `002_countries_va_via_kafka.md`)
and run largely in parallel — they converge where the org-VA create form (009) consumes the
`currency` table (010); their tests fold back into the 006 suites. Phase 011 (idea
`004_single_flow_run.md`) is a UX-focused redesign of the Phase 003/005 single-flow runner: an
additive field-descriptor catalog ([ADR-014](decisions/014-flow-catalog-field-descriptors-and-client-side-inference.md))
plus a reworked frontend; it changes no Kafka surface, table, or publish contract. Phase 012
(idea `005_trial_balance.md`) adds the **Trial Balance** report: a read-only page over a thin
read-proxy of the ledger's reporting endpoint — additive within the existing `ledgerproxy`
package ([ADR-015](decisions/015-trial-balance-via-ledger-read-proxy.md)), no new tables, Kafka,
or persistence. Phase 013 (idea `006_N_TIMES_chaos_strategy.md`) adds the **N Times** chaos
strategy — N *distinct* transactions between the same accounts (vs Burst's duplicate event) — in
the `flow.chaos` package, with a SYNC in-line path and an ASYNC path that **reuses the Phase 003
batch runner / run tables** behind a `kind` discriminator (one additive Flyway migration); it
reuses the Phase 011 `autogen` descriptors for per-iteration id re-rolling and changes no ledger
flow contract ([ADR-016](decisions/016-n-times-distinct-transaction-chaos-strategy.md)). Phase 014
(idea `007_collection_settlement_disbursement_flow.md`) re-introduces the three flows Phase 011
deferred — **Collection / Settlement / Disbursement** — flipping their `runnerVisible` flag and
adding rich descriptors (ADR-014's design intent). Settlement and Disbursement are **lifecycles**
(`initiated`→`completed`|`failed`) with an operator outcome: Succeed/Fail run as a client-side
two-step wizard over the unchanged publish path; **Random** runs unattended on a server lifecycle
runner that reuses the Phase 003/013 batch infra (`RunKind.LIFECYCLE`) and is **N-Times-capable**.
It also **corrects the previously hand-guessed Collection/Disbursement inbound models** to the
verified ledger contract (adding `DISBURSEMENT_INITIATED`/`_FAILED`, a shared `TransactionFeeLine`
with `fee_code`, and a typed `PublishFlowRequest.fees[]`), and sources the disbursement
`reservation_id` through a thin ledger reservations read-proxy poll
([ADR-017](decisions/017-lifecycle-transaction-flows-and-outcome-orchestration.md),
[ADR-018](decisions/018-reservation-id-via-ledger-read-proxy-poll.md),
[ADR-019](decisions/019-dynamic-fee-lines-and-catalog-descriptor-extensions.md)).
Phase 015 (idea `008_balance_display.md`) surfaces **ledger balances** in the VA views — a
point-in-time "Balance As Of" panel (all four buckets) on the detail page and a total-balance column
on the list views — by extending the existing `ledgerproxy` read-through: the ledger natively serves
PIT balance (`GET /accounts/{id}/balance?asOf`, reconstructed from journal-line running-balance
witnesses) and a batch lookup (`GET /balances?accountId=…`), so the chaos side adds only an `asOf`
passthrough and a `GET /ledger/balances` batch proxy, plus a `createdAt`-DESC ordering on
`/virtual-accounts` to match the Ledger tab. It introduces no tables, Kafka, or persistence and also
corrects the camelCase drift in `LedgerBalanceDto`
([ADR-020](decisions/020-as-of-balance-via-ledger-read-proxy.md),
[ADR-021](decisions/021-batch-balance-read-proxy-for-list-column.md)).
Phase 016 (idea `011_batch_disbursement.md`) adds **Batch Disbursement** as a new **fan-out
flow family** — verified against `ss-ledger-service` (`disbursement.batch.*`): one
`BATCH_RESERVATION_REQUEST` holds the batch total against the source ORG VA and declares N
items, then each item partially **captures** (completed) or **releases** (failed) its slice
from that single reservation, with the ledger deriving batch status from counters. Four new
flow types/builders ride the **unchanged** `POST /flows/{flowType}` path (the two
`disbursement.batch.initiated` phases use an `operation` discriminator and all four emit the
ledger's **structured** idempotency keys). It is driven two ways: a client **Manual** wizard
(cycle N item forms, per-item pass/fail, per-event chaos) and an **Automatic** server runner
(even split of principal/fees across N with remainder absorption + an outcome policy of
all/none/exactly-K/random) that reuses the Phase 003/013 batch runner behind
`RunKind.BATCH_DISBURSEMENT`. The batch `reservation_id` and live progress come from a thin
`GET /ledger/disbursement-batches/{batchId}` read-proxy keyed by `batch_id` (the inbound
`reservation_id` is ledger-ignored on items, as with the single flow). It adds four flow
types, one catalog grouping, one runner+endpoint, one read-proxy, and one additive Flyway
migration — no new inbound Kafka surface
([ADR-022](decisions/022-batch-disbursement-fan-out-flow-and-dual-mode-orchestration.md),
[ADR-023](decisions/023-batch-reservation-id-and-progress-via-batch-summary-read-proxy.md)).
Phase 017 (idea `012_transaction_failure.md`) adds the chaos machine's **second inbound
consumer** and is **Part 1 of a four-part series** that consumes the ledger's outbound event
surface (parts 2–4: `013_balance_history` → `ledger.balance.updated`,
`015_reservation_created` → `ledger.reservation.created`/`.released`, `014_dlt_views` → the
`.dlt` topics). It consumes **`ledger.transaction.failed`** into a `transaction_failure`
projection and **correlates** each failure to the publish that caused it. The correlation key
is verified against `ss-ledger-service`: the failure's `metadata.correlation_id` is the
ledger's *recording id* (not the chaos correlation id), so the only reliable link is
**`transaction_request_id`** — a payload field the ledger stores `unique, non-null`, sourced
from exactly the fields the chaos catalog already autogenerates (`transaction_id` for
collection/disbursement, `settlement_request_id`, `transfer_request_id`, `topup_request_id`,
`batch_id`/`item_id`). So the chaos machine already knows, at publish time, the id the ledger
will file — the publish side simply **labels, persists (a new indexed `publish_record` column),
and echoes** it (in `FlowResult`/`NTimesSyncResult`). To make room for the series, it
**generalizes the Phase 009 consumer** from a single pinned payload type to method-typed,
multi-event deserialization (one `ByteArrayJsonMessageConverter` factory; each `@KafkaListener`
declares its own `EventEnvelope<T>`; DLT derived as `<topic>.dlt`), migrating the
account-created listener onto it ([ADR-024](decisions/024-multi-event-ledger-outbound-consumer.md)).
Surfacing is two-pronged: a **bounded scoped poll → `sonner` toast** on the Single Flow Run
page when a just-published transaction is rejected (SSE considered and rejected as
disproportionate for a single-operator harness), and a **ledger Outcome column** on the
"Sent" history tab resolved with one batch `/transaction-failures` lookup per page
(distinguishing *published-to-Kafka* from *ledger-accepted*). Two additive Flyway migrations
(`transaction_failure`; `publish_record.transaction_request_id`), no new outbound Kafka
surface ([ADR-025](decisions/025-transaction-failure-projection-and-request-id-correlation.md),
[ADR-026](decisions/026-run-page-failure-surfacing-via-bounded-polling.md)). A key caveat the
design stresses: "no failure observed" is **never** a success proof (failures are
asynchronous and the poll window is finite). The ledger emits **no** per-transaction success
event keyed by `transaction_request_id`; the closest positive evidence is a
`ledger.balance.updated` landing on the involved account(s) (Part 2), but that event is
**per-account and not keyed to the publish** (see Phase 018 / ADR-027), so it confirms *an
account's balance moved*, not *which transaction succeeded*.
Phase 018 (idea `013_balance_history.md`) is **Part 2** of the series: the chaos machine's
**third inbound consumer** projects **`ledger.balance.updated`** into a per-account,
event-sourced **`balance_history`** table, served by
`GET /api/v0/virtual-accounts/{vaId}/balance-history` (+ a flat/batch
`GET /api/v0/balance-history?accountId=…&from=…`) and a new **Balance tab** on the VA detail
page. It also raises a **run-page info toast** — "Balance updated on {account}" — when a
balance moves on an account the operator just published to (reusing the Part 1 `sonner`
toaster + bounded-poll mechanism), scoped **heuristically** by involved account id(s) + a time
watermark since the event carries no `transaction_request_id`; the copy never implies
per-transaction causation (an account+time match is not a per-transaction ack — distinct from
the precise, request-id-keyed failure toast). It rides the Part 1 generalized consumer
(ADR-024) — a listener + a snake_case mirror record + one additive Flyway migration (`V14`). Verified against `ss-ledger-service`:
the event carries the four buckets + `total_debits`/`total_credits` + a per-account
`last_entry_sequence` + `balance_as_of`, but **no currency** (backfilled best-effort from the
VA registry, since `account_id` = `va_id`) and **no transaction linkage** (the payload has no
`transaction_id`/`transaction_request_id`; `metadata.correlation_id` is a fresh random UUID
and `idempotency_key` carries a journal/reservation id) — so the history is **per-account,
deliberately not correlated to publishes**. The ledger fans out **one event per affected
account** (a transfer → three events), which is exactly a per-account stream; dedup is by
envelope `event_id`, ordering by `(occurred_at DESC, last_entry_sequence DESC)`. This phase
**introduces stored balance data** — a reversal of Phase 015's read-through-only stance — but
**complements rather than supersedes** ADR-020/021: live/PIT balance stays an authoritative
ledger read-through, while `balance_history` is an observational event log of the side
effects a chaos run produces
([ADR-027](decisions/027-balance-history-projection-from-ledger-balance-updated.md)).
Phase 019 (idea `015_reservation_created.md`) is **Part 3**: the **fourth inbound consumer**
projects **both** `ledger.reservation.created` and `ledger.reservation.released` into a single
stateful **`reservation`** table (one row per `reservation_id`, monotonic-status upsert
`ACTIVE`→`PARTIALLY_RESOLVED`→`CAPTURED`|`RELEASED`|`EXPIRED`, dedup by envelope `event_id`),
**factoring in batch reservations** (one aggregate reservation emitting one created + multiple
partial-resolution releases). It adds per-VA + flat query endpoints, a **Reservations tab** on
the VA detail page, and **create/release toasts** in the settlement & disbursement (and batch)
flows. Verified against `ss-ledger-service`: one record
`ReservationLifecycleEventData(reservationId, accountId, transactionId, reservationType[SINGLE|
BATCH], amount, status, disbursementBatchId)` backs both topics (event_type + status
disambiguate); crucially its **`transaction_id` is the inbound `transactionRef` = the chaos
request id** (disbursement `transaction_id`, settlement `settlement_request_id`, batch
`batch_id`), so reservations are **precisely** correlatable to a publish and the toasts are
request-id-scoped (unlike Part 2's heuristic balance toast). Settlement **does** create a ledger
reservation internally (even though its chaos events carry no `reservation_id`), so its create
toast is valid. The event omits **currency** (backfilled best-effort from the VA registry) and
**expiry / captured-released amounts** (kept in the ADR-018 read-proxy). It **complements, not
supersedes**, ADR-018/023: the wizards keep sourcing `reservation_id` via the read-proxy poll;
this projection adds push tracking + toasts. One additive Flyway migration (`V15`)
([ADR-028](decisions/028-reservation-lifecycle-projection.md)).
Phase 020 (idea `014_dlt_views.md`) is **Part 4 — the final member** and the chaos-testing
payoff: a **tolerant DLT consumer** (its own factory, log-and-skip, **no recoverer** — it can't
itself dead-letter) ingests the ledger's **inbound** dead-letter topics
(`ledger.<flow-topic>.dlt`, 17 topics — *the deliberately-malformed traffic the chaos machine
published and the ledger rejected*) into a **single domain-tagged `dlq` table** (Flyway `V16`).
Verified against `ss-ledger-service`: the ledger dead-letters inbound consumers with a structured
`DeadLetterTopicRecord(deadLetterId, deadLetteredAt, originalTopic, originalPartition,
originalOffset, originalKey, Failure{classification, exceptionType, message, retryCount},
originalEvent)` — one rich uniform format carrying the error class/reason, retry count, and the
original payload, mapping directly onto the idea's Overview/Message tabs. Dedup is by
`(dlt_topic, partition, offset)`; `domain` is derived from the original topic; `transaction_id`/
`type` are best-effort-extracted from the original payload (null for `DESERIALIZATION`-class dead
letters, whose original was unparseable). It adds `GET /api/v0/dlq` (filters: domain /
transaction id / transaction type) + `/{id}`, and a **"Dead Letter Queue"** nav item under
*Operate* → filterable list → tabbed detail. A **configurable explicit topic list** (not a
`ledger\..*\.dlt` pattern) keeps the format uniform and excludes the chaos-machine's **own**
outbound-event DLTs (a different Spring-standard format), which are deliberately out of scope and
foldable in later behind a `source` discriminator without a migration
([ADR-029](decisions/029-dead-letter-queue-projection.md)). With this, the four-part "testing
ledger Kafka events" series is complete: the chaos machine consumes the ledger's failure
(`017`), balance (`018`), and reservation (`019`) outbound events, and observes the dead letters
of its own inbound traffic (`020`).

## 9. Cross-cutting non-functional posture

- **Resilience (of the harness itself):** idempotent Kafka producer (`acks=all`,
  `enable.idempotence=true`), bounded batch concurrency with backpressure, ledger-proxy
  timeouts + retries + circuit breaker. The harness must stay healthy while *deliberately*
  stressing the ledger.
- **Observability:** Actuator + Micrometer/Prometheus, structured JSON logs
  (`logstash-logback-encoder`), correlation-id propagation into every published event.
- **Security:** all `/api/v0/**` require a verified AUTH SERVICE token; CSRF disabled
  (stateless); secrets via env. The destructive "chaos" endpoints sit behind the same auth.
- **Safety rails:** chaos runs are explicit, bounded (max rate / max count), and target a
  configurable Kafka cluster so production is never an accidental target.

## 10. Open questions & documented assumptions

1. **MANIFEST is truncated** (ends mid-sentence). Transactions search is assumed to filter
   by VA id, flow/event type, correlation id, date range, and status.
2. **Account-code fixes:** `PROVIDER_FEE` code was blank → assumed `REVENUE.PROVIDER.FEE`;
   `PLATFORM_FLOAT_MTN` duplicated the TELECEL code → assumed `ASSET.PLATFORM.FLOAT.MTN`.
3. ~~**VA creation "via Kafka"** has no dedicated inbound topic; modeled as publishing
   `organization.onboarded` and `organization.va.updated`.~~ **Superseded by
   [Phase 009](phases/009-ledger-owned-virtual-accounts/DESIGN.md) ([ADR-011](decisions/011-ledger-owned-virtual-accounts-via-kafka-consumer.md)):**
   the ledger *does* publish a dedicated **`ledger.account.created`** event (+ `.dlt`) — verified in
   `ss-ledger-service` (`account/events/v1/AccountCreatedEventData`,
   `account/events/AccountCreatedEventFactory`). The chaos machine now **consumes** it (its first
   Kafka consumer) to materialize VAs; the ledger owns VAs. VA-create + CoA bootstrap issue
   `POST /api/v0/accounts` to the ledger and never persist VAs directly.
4. ~~**`disbursement.completed` is a proposed contract.**~~ **Resolved by
   [Phase 014](phases/014-collection-settlement-disbursement-flows/DESIGN.md)
   ([ADR-019](decisions/019-dynamic-fee-lines-and-catalog-descriptor-extensions.md)):** the
   disbursement (and collection) contracts are now **verified against `ss-ledger-service` source**
   and `bin/kafka-payload-samples.md`. Disbursement is a **lifecycle** (`initiated`→`completed`|
   `failed`, `source = payment-service`), not a single symmetric event. The prior hand-guessed
   chaos model was wrong and is rewritten; the corrected field sets live in
   [Phase 014 / task 001](phases/014-collection-settlement-disbursement-flows/001-inbound-contract-alignment-and-lifecycle-models.md).
5. **Chart of accounts is provisioned via the ledger HTTP API** ([Phase 007](phases/007-chart-of-accounts-http-bootstrap/DESIGN.md));
   VA ids are ledger-assigned. If the ledger seeds its own SYSTEM accounts, set
   `chaos.bootstrap.provision-on-startup=false` and the chaos machine adopts existing ids by code.
6. **`organization.onboarded` `country` object carries `status` + `modified_date`** in the
   authoritative ss-ledger-service sample (`bin/publish-organization-onboarded.sh`), which the
   current `OrganizationOnboardedEventData.Country` record omits. [Phase 008](phases/008-organization-onboarding/DESIGN.md)
   extends that record to match. The sample also uses an **ISO 3166-1 alpha-2** `iso_code` (`GH`),
   so the country `iso_code` column is widened/relaxed from the prior 3-char assumption. Verified
   against the sibling repo; re-confirm if the ledger contract changes.

7. **`organization.onboarded` carries currency, the ledger does not read it yet.**
   [Phase 010](phases/010-currencies-and-supported-countries/DESIGN.md) ([ADR-012](decisions/012-currency-and-supported-country-reference-model.md))
   adds a top-level `currency {id, code}` to the payload from the country's primary currency. The
   ledger's `OnboardedEventData` has **no** currency field and its org-VA provisioner hardcodes
   `GHS` (verified in `ss-ledger-service`); the extra field is safe while the ledger keeps Jackson's
   default `fail-on-unknown-properties=false`. Coordinate when the ledger starts honouring currency.
8. **Deletion of organizations is deferred.** The idea (`002_countries_va_via_kafka.md`) lists
   "support deletion of organizations"; per product decision this is **deferred to a future phase
   that handles virtual-account statuses/lifecycle** (since orgs drive VAs the ledger owns). Not in
   Phase 009/010.
9. **Currency representation:** chaos models currency as a managed table with a **UUID id + ISO-4217
   `code`** ([ADR-012](decisions/012-currency-and-supported-country-reference-model.md)); the
   ledger stores account currency as a plain ISO-4217 string. They reconcile by `code`. The
   `ledger.account.created` projection stores the ledger's currency code as-is and does not require
   a matching `currency` row to exist.
10. **External reference-data dependency:** countries + currencies are **seeded at startup from
    [restcountries.com](https://restcountries.com/)** (`/v3.1/all?fields=name,cca2,cca3,currencies`)
    per [ADR-013](decisions/013-seed-countries-and-currencies-from-restcountries-api.md). The fetch
    is async + seed-if-empty + degrade-on-failure, so a slow/unreachable API never blocks or breaks
    boot; a cold first boot with no network leaves the country list empty until a retry or
    `POST /api/v0/countries/refresh`. Base URL is configurable to a mirror/self-host; the API
    requires the `fields` param and returns the first-of-many currency as a country's primary.

11. **Disbursement `reservation_id` is ledger-assigned and ledger-ignored on the wire.** Verified
    in `ss-ledger-service`: the ledger creates the reservation on `disbursement.initiated`
    (`reservation_id = UUID.randomUUID()`, keyed by `transactionRef = transaction_id`), and on
    `disbursement.completed`/`failed` it looks the reservation up **by `transaction_id`** and
    **ignores the inbound `reservation_id`** (mismatches silently use the stored one). So the
    lifecycle linkage that matters is `transaction_id`; the required `reservation_id` field is
    cosmetic. [Phase 014](phases/014-collection-settlement-disbursement-flows/DESIGN.md)
    ([ADR-018](decisions/018-reservation-id-via-ledger-read-proxy-poll.md)) still fetches the
    **real** id for fidelity by polling the ledger's
    `GET /api/v0/accounts/{accountId}/reservations?transactionRef=…` via a read-proxy (timeout →
    manual/placeholder). **Confirm that ledger read endpoint's exact path/param/shape** before
    implementing. The ledger also publishes `ledger.reservation.created` — a future
    higher-fidelity option not used here.

12. **Settlement-completed destination field is `settlement_va_id` (confirmed).** The chaos
    `SettlementCompletedFlowBuilder` *and* `bin/kafka-payload-samples.md` currently use
    **`destination_va_id`** — so today's settlement-completed flow is sending the wrong field
    name. [Phase 014](phases/014-collection-settlement-disbursement-flows/DESIGN.md)
    ([ADR-019](decisions/019-dynamic-fee-lines-and-catalog-descriptor-extensions.md)) corrects it
    to `settlement_va_id` (+ `source_va_id` + `source_organization_id` + required
    `completion_reference`).

13. **`disbursement.completed` destination role:** the idea says "System Settlement Account"; the
    ledger sample comment says platform float. Both are SYSTEM accounts; the slot defaults to
    `SETTLEMENT_ACCOUNT` and is operator-overridable. Confirm with product.

14. **`ledger.transaction.failed` correlation key + naming trap (Phase 017).** Verified in
    `ss-ledger-service`: `TransactionFailedEventData(transactionId, transactionRequestId,
    transactionType, failureCode, failureReason)`; the failure's `metadata.correlation_id` is
    set to the ledger **recording id**, *not* the chaos outbound correlation id — so failures
    correlate to publishes **only** by `transaction_request_id`. The ledger sources
    `transactionRequestId` from a *payload* field per inbound event (`transaction_id` for
    collection/disbursement, `settlement_request_id`, `transfer_request_id`, `topup_request_id`,
    `batch_id`/`item_id`) — matching the chaos catalog's autogen fields exactly. **Naming trap:**
    in the failure event `data.transaction_id` is the ledger's recording UUID while
    `data.transaction_request_id` is the chaos-supplied id (and for collection/disbursement the
    chaos *payload field is named* `transaction_id`). The producer emits with
    `setAddTypeInfo(false)` (no JSON type headers) and snake_case. Re-confirm the field set if
    the ledger contract changes. Also note: **absence of a failure is not a success signal**
    (failures are asynchronous and the poll window is finite) — and the ledger emits **no**
    per-transaction success event keyed by `transaction_request_id` (see #15).

15. **`ledger.balance.updated` is per-account, not transaction-correlatable (Phase 018).**
    Verified in `ss-ledger-service`: `AccountBalanceUpdatedEventData(accountId,
    availableBalance, pendingBalance, reservedBalance, totalBalance, totalDebits, totalCredits,
    lastEntrySequence, balanceAsOf)` — **no `currency`** and **no transaction id**. The only
    causal hint is `metadata.idempotency_key` (`{journalEntryId}:{accountId}` /
    `{reservationId}:…`); `metadata.correlation_id` is a fresh random UUID. So a balance update
    **cannot** be keyed back to a chaos publish by `transaction_request_id` — Phase 018 stores it
    as a **per-account** history (`account_id` = `va_id`), backfilling `currency` from the VA
    registry, deduping by envelope `event_id`, ordering by `(occurred_at, last_entry_sequence)`.
    The ledger fans out **one event per affected account** (a transfer → three events) and only
    on a committed balance mutation (incl. reservation RELEASE/EXPIRY; CAPTURE is
    balance-neutral). This **corrects** an earlier note that called `ledger.balance.updated` a
    "definitive per-transaction success signal" — it confirms an account's balance moved (and
    the post-state), not which transaction caused it. Re-confirm the field set if the contract
    changes.

16. **`ledger.reservation.created`/`.released` are transaction-correlatable (Phase 019).**
    Verified in `ss-ledger-service`: one record
    `ReservationLifecycleEventData(reservationId, accountId, transactionId, reservationType
    [SINGLE|BATCH], amount, status[ACTIVE|PARTIALLY_RESOLVED|CAPTURED|RELEASED|EXPIRED],
    disbursementBatchId)` backs **both** topics (the envelope `event_type` + `status`
    disambiguate). Its **`transaction_id` is the inbound `transactionRef`** the publisher
    supplied — disbursement `transaction_id`, settlement `settlement_request_id`, batch
    `batch_id` — i.e. the chaos request-id fields (cf. #14/#15), so reservations **can** be keyed
    back to a publish (unlike `ledger.balance.updated`). **Batch** = one aggregate reservation
    (`reservationType=BATCH`, `disbursement_batch_id` set, `amount`=batch total) emitting **one
    created + multiple released** events as items resolve. **Settlement creates a reservation**
    internally even though its chaos events carry no `reservation_id` on the wire (cf. ADR-018).
    The event **omits currency and expiry** (and captured/released amounts) — those remain on the
    ledger read-proxy (`/ledger/accounts/{id}/reservations`, `ReservationResponse`), which
    Phase 019 keeps for `reservation_id` sourcing while adding a push-fed `reservation` projection
    for tracking + toasts. `metadata.correlation_id` is random; dedupe by envelope `event_id`;
    events keyed by `reservation_id`. Re-confirm the field set / status enum if the contract
    changes.

17. **Dead-letter topology + which DLTs the chaos `dlq` ingests (Phase 020).** Verified in
    `ss-ledger-service`: the ledger dead-letters **inbound** consumers to `ledger.<original-topic>.dlt`
    (17 topics) with a structured JSON value `DeadLetterTopicRecord(deadLetterId, deadLetteredAt,
    originalTopic, originalPartition, originalOffset, originalKey, Failure{classification,
    exceptionType, message, retryCount}, originalEvent: EventEnvelope<JsonNode>)` (retry policy
    max-attempts=4, exp 1s×2; 14-day DLT retention). The ledger's **outbound**-event `.dlt`
    topics (`ledger.account.created.dlt`, `ledger.transaction.failed.dlt`, …) are populated only
    by a failed **consumer** — i.e. the chaos machine's own Parts 1–3 consumers, via Spring's
    stock recoverer (original value + `kafka_dlt-*` headers — a **different** format). **Phase 020
    ingests the ledger inbound DLTs only** (product-confirmed): the chaos-testing payoff, one rich
    uniform format. It uses a **configurable explicit topic list** (not a `ledger\..*\.dlt`
    pattern, which would also pull the chaos-own outbound DLTs) and a **tolerant, recoverer-less**
    consumer (a DLT viewer must never itself dead-letter). The `dlq` table is domain-tagged and
    format-agnostic, so the chaos-own DLTs can be folded in later behind a `source` discriminator
    (a second format mapping) without a migration. Re-confirm the `DeadLetterTopicRecord` shape /
    topic names if the contract changes.

These are safe-to-proceed defaults; revise here if the complete MANIFEST / ledger contract differs.
