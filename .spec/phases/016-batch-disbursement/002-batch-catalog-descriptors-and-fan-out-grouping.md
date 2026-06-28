# Task 002 - Batch catalog descriptors & fan-out grouping metadata (backend)

## Functional Requirements
- Add the `GET /api/v0/flows/catalog` descriptors for the four batch flow types, so the
  frontend renders the reservation and item forms entirely from the catalog (ADR-014).
- Introduce a **`BatchDisbursementGroup`** catalog record (the fan-out analogue of
  `FlowLifecycle`) that groups the four phases and declares the **carry-over** maps the
  wizard/runner use to build item events from the reservation and from the item request.
- Make **only** the reservation entry `runnerVisible` (carrying the group) so the radio shows
  a single **"Batch Disbursement"** choice; the other three phases keep full descriptors but
  are not standalone radio choices.
- Add a small **`INTEGER`** `FieldKind` for `item_count` (and reuse it for the automatic
  outcome `passCount`), and SELECT option lists for `disbursement_subtype` and `failure_code`.

## Acceptance Criteria
- [ ] `FlowCatalogEntry` gains an optional `batchGroup` field (`BatchDisbursementGroup`),
      mutually exclusive with `lifecycle` (a flow is single-shot, a lifecycle, **or** a batch
      group — at most one non-null).
- [ ] `BatchDisbursementGroup(label, reservation, itemRequest, itemCompleted, itemFailed,
      reservationToItem[], itemRequestToTerminal[])` exists (`@RecordBuilder`), with
      `label = "Batch Disbursement"` and the four `FlowType`s wired.
- [ ] `reservationToItem` carries: `batch_id`, `batch_correlation_id`, `merchant_id`,
      `reservation_id`, source VA → `virtual_account_id`, `currency`, `disbursement_subtype`,
      `correlation_id`.
- [ ] `itemRequestToTerminal` carries: `item_id`, `item_sequence`, `principal_amount`,
      `item_fee` → fee line amount, `virtual_account_id`, `disbursement_subtype`,
      `merchant_item_ref`, `provider_id`, `corridor`/`destination_country`.
- [ ] Exactly `DISBURSEMENT_BATCH_RESERVATION_REQUEST` is `runnerVisible = true` and carries
      `batchGroup`; the three other batch entries are `runnerVisible = false` with full
      descriptors.
- [ ] `FieldKind` gains `INTEGER`; the reservation form's `item_count` uses it (required,
      default per config, min 1, max `chaos.limits.maxBatchItems`).
- [ ] Reservation descriptors include: `source` VA_REF (`accountKind = ORGANIZATION`,
      `slotName = source`), `destination` VA_REF (`accountKind = SYSTEM`, `slotName =
      destination`), `total_principal_amount` (AMOUNT, default `1000.0000`), `total_fees`
      (AMOUNT, default `10`), `item_count` (INTEGER), `disbursement_subtype` (SELECT
      `[DOMESTIC, CROSS_BORDER]`), autogen `batch_id`/`batch_correlation_id` (UUID_V4),
      `merchant_batch_ref` (ULID); advanced/inferred: `merchant_id` (inference
      `ORG_FROM_SOURCE_VA`), `currency` (`CURRENCY_FROM_SOURCE_VA`), `correlation_id`
      (UUID_V4), `callback_url`, `authorised_user_id`/`authorised_key_fingerprint`.
- [ ] Item-completed/failed descriptors include `fees[]` (FEE_LIST), `provider_id`,
      `provider_reference_id` (autogen ULID), `recipient_reference`, `failure_reason`,
      `failure_code` (SELECT with the seven ledger codes), plus the carried/advanced fields;
      item-request descriptors include `principal_amount`, `item_fee`, `credit_provider_id`,
      `credit_account_id`, `source_country`/`destination_country` (COUNTRY),
      `fx_quote_reference`.
- [ ] Derived legacy `requiredFields`/`optionalFields`/`csvColumns` are produced for each new
      flow (consistent with how existing flows derive them), so nothing downstream NPEs.

## Technical Design
Target Java 25 / Spring Boot 4. The catalog stays a server-assembled static structure
(`FlowCatalogProvider.catalog()`); adding a batch group is descriptor data, not new renderer
logic — the radio still shows `runnerVisible` entries, and the frontend special-cases
`batchGroup != null` to launch the batch flow (task 005/006) just as it special-cases
`lifecycle != null` for the lifecycle wizard.

```mermaid
flowchart TB
  entry["FlowCatalogEntry (reservation)<br/>runnerVisible=true"] --> grp["BatchDisbursementGroup"]
  grp --> r["reservation: DISBURSEMENT_BATCH_RESERVATION_REQUEST"]
  grp --> ir["itemRequest: DISBURSEMENT_BATCH_ITEM_REQUEST"]
  grp --> ic["itemCompleted: DISBURSEMENT_BATCH_ITEM_COMPLETED"]
  grp --> if2["itemFailed: DISBURSEMENT_BATCH_ITEM_FAILED"]
  grp -->|reservationToItem[]| co1["CarryOver copies"]
  grp -->|itemRequestToTerminal[]| co2["CarryOver copies"]
```

## Implementation Notes
- `flow/dto/BatchDisbursementGroup.java`: new `@RecordBuilder` record (fields above), reusing
  the existing `CarryOver(fromField, toField)`.
- `flow/dto/FlowCatalogEntry.java`: add `@Nullable BatchDisbursementGroup batchGroup`.
- `flow/dto/FieldKind.java`: add `INTEGER`.
- `flow/builder/FlowCatalogProvider.java`: add a `batchDisbursementGroup()` assembler and the
  four entries; wire the group onto the reservation entry; set `runnerVisible` flags. Mirror
  the existing `disbursementLifecycle()` assembly style.
- Keep VA_REF `slotName` set for slot-routed pickers (reservation source/destination, item
  terminal source/destination); fee VAs live inside FEE_LIST rows (no fixed slot). Single-VA
  item fields (`virtual_account_id`) route to `flowFields` with `slotName = null`, per the
  Phase 014 convention.
- Update the frontend `api.ts` types in tasks 005/006 to mirror `batchGroup` + `INTEGER`.

## Non-Functional Requirements
- Catalog assembly stays pure and side-effect-free; one network-free `GET`. Descriptor
  contract is additive — existing catalog consumers ignore unknown fields.

## Dependencies
- **Task 001** (the four flow types/builders the descriptors describe). Unblocks all frontend
  tasks (005/006) and informs the runner's carry-over (004).

## Risks & Mitigations
- **Missing slot for a `VA_REF`** → a catalog test asserts a bootstrap slot row exists for
  every `VA_REF.slotName` across the four flows (the Phase 011 trap).
- **`INTEGER` kind unhandled on the frontend** → covered by task 005/006 renderer + a typed
  `api.ts` union update.
- **Group/lifecycle ambiguity** → a test asserts at most one of `lifecycle`/`batchGroup` is
  non-null per entry, and exactly one batch entry is `runnerVisible`.

## Testing Strategy
JUnit 5 + AssertJ: catalog contains the four batch entries with the exact descriptor
kinds/required/advanced/autogen/inference/accountKind/slotName/options; `INTEGER` used for
`item_count`; `batchGroup` present only on the reservation entry and `runnerVisible` there
only; carry-over maps contain the listed pairs; derived legacy lists are non-empty and match
the field sets. A WebMvc slice test on `GET /flows/catalog` (auth + shape).

## Deployment Strategy
Additive catalog fields + one new `FieldKind`; no migration, no flag. Ships with task 001.
