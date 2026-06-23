# Task 002 - Synchronous N-Times Execution

## Functional Requirements
- Execute a `mode = SYNC` N-Times request **in-line** on the request thread: run the flow `count`
  times **sequentially**, publishing one distinct transaction per iteration, applying the pacing
  delay **between** iterations.
- Reuse the existing per-iteration pipeline (`FlowEngine` build → `ChaosPlan.expand` → publish →
  history) so each N-Times iteration is published and recorded exactly like a normal single flow,
  but labelled as part of an N-Times run.
- Return an **aggregate result** summarizing the run (count, succeeded, failed, the shared
  correlation id, and the per-iteration event/history ids).
- Guard against abuse: reject SYNC runs that are too large or would run too long, pointing the
  caller at ASYNC.
- Expose a dedicated endpoint `POST /api/v0/flows/{flowType}/n-times`; this task owns its **SYNC**
  branch. Reject `chaos.nTimes` on the plain `POST /api/v0/flows/{flowType}` endpoint.

## Acceptance Criteria
- [ ] A SYNC N-Times request publishes exactly `count` events, each with a **distinct** idempotency
      key (`"<event-type>:<eventId>"`) and a **distinct** `*_request_id`, all sharing **one**
      correlation id.
- [ ] Pacing is honoured: `BURST` → no delay between iterations; `LINEAR` → `fixedDelayMs` gap;
      `RANDOM` → a gap in `[minDelayMs, maxDelayMs]`.
- [ ] Each publish writes a `publish_record` with `chaos_strategy = "NTIMES:<pacing>:<i>/<count>"`
      and the shared `correlation_id`; a failed publish is recorded and counted but does not abort
      the remaining iterations.
- [ ] The endpoint returns `200` with an aggregate body
      `{ flowType, count, succeeded, failed, correlationId, eventIds[], historyIds[] }`.
- [ ] SYNC rejects (`400`) when `count > maxNTimesSync`, or when projected
      `count × effectiveMaxGap > maxSyncDurationMs` (message names the ASYNC alternative).
- [ ] `POST /api/v0/flows/{flowType}` returns `400` if the body carries `chaos.nTimes`.
- [ ] All other chaos strategies and the single-publish path are unaffected.

## Technical Design
Target **Java 25**, Spring Boot 4.

Add `FlowEngine.executeNTimes(FlowRequest base)` (or a small `NTimesSyncRunner` collaborator that
the engine/controller calls). Flow:

```mermaid
sequenceDiagram
  participant C as FlowController (/n-times)
  participant X as NTimesExpander
  participant E as FlowEngine
  participant P as ChaosEventPublisher
  participant H as HistoryWriter
  C->>X: validate(nTimes) + guard SYNC duration/count
  C->>X: expand(base) → [req_1..req_N] (shared correlationId)
  loop i = 1..N
    alt i > 1
      C->>C: sleep delayFor(nTimes, i)  // BURST=0, LINEAR=fixed, RANDOM∈[min,max]
    end
    C->>E: execute(req_i)  // fresh eventId → idempotency key; nTimes stripped from chaos
    E->>P: publish(envelope_i)
    E->>H: record(..., "NTIMES:<pacing>:i/N", false)
  end
  C-->>C: aggregate(count, succeeded, failed, correlationId, eventIds, historyIds)
```

Key points:
- The expander (Task 001) produces N `FlowRequest`s whose `chaos` is **null / nTimes-stripped**,
  so each `FlowEngine.execute(req_i)` runs the normal single path (one `PreparedSend`) — no
  recursion. Set the per-iteration chaos label by passing the `NTIMES:...` label through; the
  simplest seam is a small overload/parameter on the execute path that stamps the chaos label,
  or wrap each `execute` and post-tag — see Implementation Notes for the chosen seam.
- The **inter-iteration delay** is applied by the sync runner (not `ChaosPlan`), using the
  engine's existing virtual-thread-friendly `Thread.sleep(Duration)` pattern
  (`FlowEngine.applyDelay`). This realises true linear/random spacing and **does not** reproduce
  Burst's cumulative-delay quirk.
- The **duration guard**: `effectiveMaxGap = switch(pacing){ BURST→0; LINEAR→fixedDelayMs;
  RANDOM→maxDelayMs }`; reject if `count × effectiveMaxGap > limits.maxSyncDurationMs` or
  `count > limits.maxNTimesSync`.

## Implementation Notes
Files to create:
- `flow/NTimesSyncResult.java` (or `flow/dto/`) — `@RecordBuilder record NTimesSyncResult(
  FlowType flowType, int count, int succeeded, int failed, String correlationId,
  List<String> eventIds, List<String> historyIds)`.
- (Recommended) `flow/NTimesSyncRunner.java` — `@Component` owning the SYNC loop, injecting
  `NTimesExpander`, `FlowEngine`, `ChaosLimits`. Keeps `FlowEngine` lean.

Files to modify:
- `flow/controller/FlowController.java` — add
  `@PostMapping("/{flowType}/n-times")` returning `ResponseEntity<?>` that, for `mode == SYNC`,
  calls the sync runner and returns `200` with `NTimesSyncResult`; the `ASYNC` branch is Task 003.
  Map `PublishFlowRequest` → `FlowRequest` exactly as the existing `publish(...)` does (reuse a
  private helper). Validate `request.chaos() != null && request.chaos().nTimes() != null` (else
  `400`).
- `flow/controller/FlowController.java#publish` — reject when `request.chaos() != null &&
  request.chaos().nTimes() != null` with a `BadRequestException` pointing at `/n-times`.
- `flow/FlowEngine.java` — provide a seam to stamp the chaos label per iteration. Lowest-touch
  option: add `FlowResult execute(FlowRequest request, @Nullable String chaosLabelOverride)`; the
  existing `execute(request)` delegates with `null`. When the override is present, pass it as the
  `chaosLabel` to `HistoryWriter.record(...)` (the engine currently passes `send.chaosLabel()` —
  prefer the override when non-null). `intentionalFailure` stays `false` (N-Times sends are
  well-formed).

Notes:
- Aggregate by collecting each iteration's `FlowResult` (status, eventId, historyId).
- `succeeded`/`failed` come from `FlowResult.status()` per iteration; never throw out of the loop
  for a single publish failure (best-effort, mirrors batch row semantics).
- The shared correlation id is the expander's output; surface it in the result.

## Non-Functional Requirements
- Runs on the caller's (virtual) request thread; the duration guard keeps worst-case wall-clock
  ≤ `maxSyncDurationMs`, protecting against gateway timeouts.
- No change to producer durability; each iteration uses the idempotent producer path.
- Memory bounded by `count ≤ maxNTimesSync` aggregated results.

## Dependencies
- **Task 001** (`NTimesOptions`, `NTimesExpander`, extended `ChaosLimits`).
- Existing `FlowEngine`, `ChaosEventPublisher`, `HistoryWriter`, `FlowController`,
  `PublishFlowRequest`, `FlowRequestBuilder`.
- [ADR-016](../../decisions/016-n-times-distinct-transaction-chaos-strategy.md).

## Risks & Mitigations
- *Long SYNC run blocks the request* → duration + count guards reject and steer to ASYNC.
- *Label-stamping seam bloats `FlowEngine`* → keep the loop in `NTimesSyncRunner`; add only a
  minimal labelled-execute overload to the engine.
- *Partial failures misreported* → explicit per-iteration status capture + AssertJ assertions on
  `succeeded + failed == count`.

## Testing Strategy
- **Unit** (`NTimesSyncRunner` with mocked `FlowEngine`): N executes, label format
  `NTIMES:<pacing>:i/N`, pacing delays applied between iterations (verify via injected clock/sleep
  seam or by asserting `delayFor` usage), guard rejections (`maxNTimesSync`, duration) → 400.
- **Integration (Testcontainers Kafka)**: SYNC `BURST`/`LINEAR` of N publishes N records;
  assert **N distinct idempotency keys** and **N distinct `*_request_id`s** with **one**
  correlation id by reading `publish_record`s; assert aggregate counts.
- **MockMvc/WebTestClient**: `POST /flows/{flowType}/n-times` SYNC → `200` aggregate; oversized
  count/duration → `400`; `POST /flows/{flowType}` with `chaos.nTimes` → `400`.

## Deployment Strategy
Additive endpoint + response type; no migration. Ships within Phase 013. The plain publish
endpoint's contract is unchanged for clients that never set `chaos.nTimes`.
