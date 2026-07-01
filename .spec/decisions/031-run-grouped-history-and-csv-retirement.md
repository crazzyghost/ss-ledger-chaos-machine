# ADR 031 - Run-grouped history (`GET /api/v0/runs`) and CSV-batch retirement

## Status
Accepted

## Context
The Scenario Runner's **Run History** tab ([ADR-030](030-unified-scenario-runner-navigation.md))
must show published events **grouped by the run that produced them**, expandable to the run's
individual events. Two existing surfaces are being merged into it, and the CSV-batch capability is
being retired — both decisions interact with the **shared** run-tracking infrastructure, so they
are recorded together.

**What exists today (verified in `chaos-machine/src` + `chaos-admin/src`):**

- **Event-level history** — `publish_record` (one row per published event), queried by
  `GET /api/v0/history` with filters incl. `batchId` and `correlationId`; rendered flat (no
  grouping) by the *Sent (Chaos History)* tab.
- **Run-level tracking** — `batch_run` / `batch_row` (Flyway `V4`, extended `V10`/`V11`), a
  `kind` discriminator (`RunKind` ∈ {`CSV`, `N_TIMES`, `LIFECYCLE`, `BATCH_DISBURSEMENT`}), a
  shared async executor `BatchRunner` + `PacingPlan`, and `GET /api/v0/batches` (list) /
  `/batches/{id}` / `/batches/{id}/rows`. The *Batches* page already lists **all** run kinds and
  `batch-run-page.tsx` already renders + live-polls **all** run kinds.
- **What is NOT tracked as a run:** a one-shot single publish, an **N-Times SYNC** run, and the
  interactive **lifecycle/batch wizards** — these write only `publish_record` rows (no
  `batch_run`); their only intrinsic linkage is `correlation_id` (and, post Phase 017, the
  indexed `transaction_request_id`).

**Run producers and the shared infra (the retirement hazard):**

| `RunKind` | Producer | Writes `batch_run`? |
|---|---|---|
| `CSV` | `BatchService.createBatch` (CSV upload) + `CsvFlowParser` | yes |
| `N_TIMES` | `NTimesRunService` (async) | yes |
| `LIFECYCLE` | `LifecycleRunService` (RANDOM outcome) | yes |
| `BATCH_DISBURSEMENT` | `BatchDisbursementRunService` (automatic) | yes |

All four ride the **same** `BatchRunner`, `PacingPlan`, `batch_run`/`batch_row` tables,
repositories, and `BatchRunResponse`/`BatchRowResponse` DTOs. Only the **CSV ingest path** is
CSV-specific. So "retire CSV (frontend + backend)" must mean *retire the CSV ingest path*, **not**
the run-tracking infrastructure the other three depend on.

The product decisions taken (operator-confirmed): **(a)** group with a **backend** run endpoint
(correct counts + pagination across runs, vs. a frontend group-within-page that double-counts runs
straddling a page boundary); **(b)** retire CSV on both tiers.

## Decision

### 1. A unified run feed: `GET /api/v0/runs`
Add a read-only `GET /api/v0/runs` (paginated, `created_at` DESC) that returns one row per **run**,
where a run is one of:

- a **tracked run** — a `batch_run` row (keyed by `batch_run.id`); summary fields come straight
  from `batch_run` (`kind`, `flow_type`, totals, status, timestamps, `external_batch_id`); or
- an **untracked run** — a group of `publish_record` rows with `batch_id IS NULL`, grouped by
  `correlation_id`; summary fields are aggregated (event count, distinct event types, status
  rollup, `intentional_failure` any, min/max `created_at`, a derived label e.g. `SINGLE` for a
  one-event group).

The group key is therefore **`COALESCE(batch_id, correlation_id)`**, with `batch_run` as the
authoritative source for tracked runs and `publish_record` aggregation for the rest. **Drill-down
(expand a run) reuses the existing API** — no new child endpoint:

- tracked run → `GET /api/v0/history?batchId={id}`
- untracked run → `GET /api/v0/history?correlationId={cid}`

The run-detail / live-progress page for **tracked** runs keeps using `GET /api/v0/batches/{id}`
(+ `/rows`); Run History rows for tracked runs deep-link to it (re-homed under
`/chaos/scenario-runner/runs/:runId`). No new tables, no Flyway migration — `/runs` reads existing
tables.

### 2. Retire only the CSV ingest path
**Remove** (frontend + backend):

- Backend: `POST /api/v0/batches` (multipart upload) from `BatchController`, `BatchService` (CSV
  parse + run creation), `CsvFlowParser`, and `GET /api/v0/batches` (the run **list** — superseded
  by `/runs`). The `csvColumns` catalog field and CSV-specific tests go with them.
- Frontend: `batch-upload-page.tsx`, the *Batches* list page (`batches-page.tsx`, superseded by
  Run History), the `startBatch()` API client, and the `/chaos/upload` route + every "New Batch"
  link.

**Preserve** (shared run-tracking — the other three run kinds depend on it): `batch_run` /
`batch_row` tables and their columns, `BatchRunRepository` / `BatchRowRepository`, `BatchRunner`,
`PacingPlan`, `BatchRunStatus` / `BatchRowStatus`, `BatchRunResponse` / `BatchRowResponse`, the
three run services (`NTimesRunService`, `LifecycleRunService`, `BatchDisbursementRunService`),
`GET /api/v0/batches/{id}` + `/batches/{id}/rows`, and the `batch-run-page.tsx` detail/progress
view (re-homed, not deleted).

Keep `RunKind.CSV` as an enum value and the `batch_run.filename` column so **historical** CSV runs
(if any in a deployed DB) remain readable — **no destructive migration**.

## Consequences
- **Positive:** Run History becomes the single, correct, paginated run view (no double-counting
  across pages); the CSV attack/ingest surface is gone end-to-end; **no new tables and no Flyway
  migration** (the feed is a read over existing data, and CSV retirement avoids dropping columns);
  the three real-flow async runners and their mature detail/progress page are untouched.
- **Negative / trade-offs:**
  - `GET /api/v0/runs` must **union two sources** (`batch_run` ∪ correlation-grouped
    `publish_record`) and paginate the union by time — more query complexity than a single-table
    list; it is read-only and indexed (`batch_id`, `correlation_id`, `created_at` already exist),
    but is the most intricate part of the phase and warrants focused tests.
  - **Interactive multi-event runs group only when their events share a `correlation_id`.** The
    lifecycle/batch wizards must therefore emit a **stable `correlation_id` across a run's steps**
    (verify current behaviour; align if needed). `transaction_request_id` (Phase 017, indexed) is
    the documented fallback grouping key if a shared correlation id is impractical.
  - `/batches/{id}` lingers as the tracked-run **detail** path even though the list moves to
    `/runs` — a small naming inconsistency accepted to avoid rewriting the detail page; an optional
    `/runs/{id}` alias is noted as future cleanup.
  - The bulk **CSV publish** capability is removed from the product; N-Times / lifecycle-random /
    batch-disbursement-automatic remain the supported "many events" paths.
