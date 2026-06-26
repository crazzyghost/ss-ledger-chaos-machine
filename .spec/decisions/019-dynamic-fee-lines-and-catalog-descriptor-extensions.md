# ADR 019 — Dynamic fee lines, catalog descriptor extensions, and inbound-contract correction

## Status
Accepted — 2026-06-25 — Phase [014 — Collection, Settlement & Disbursement Flows](../phases/014-collection-settlement-disbursement-flows/DESIGN.md)

Extends the field-descriptor catalog of
[ADR-014](014-flow-catalog-field-descriptors-and-client-side-inference.md).

## Context

Wiring Collection / Settlement / Disbursement into the Single Flow Run surfaced three
problems the existing catalog + models cannot express:

1. **The existing Collection & Disbursement models do not match the ledger contract.**
   Comparing the chaos `v1` records and builders against the authoritative
   `ss-ledger-service` event-data records (and the `bin/kafka-payload-samples.md`):

   | Flow | Chaos today (wrong) | Ledger contract (authoritative) |
   |---|---|---|
   | `collection.completed` | `collection_request_id`, `merchant_reference`, `provider_collection_id`, fee `(fee_type, amount, destination_va_id)` | `transaction_id`, `provider_id`, `provider_reference_id`, `commission_split_id`, `completed_at`, `merchant_ref_id`, fee `(fee_type, amount, fee_code, destination_va_id)` |
   | `disbursement.completed` | `disbursement_request_id`, `organization_id`, `recipient_account_number`, `recipient_bank`, `provider_disbursement_id`, single derived fee | `transaction_id`, `reservation_id`, `disbursement_subtype`, `provider_id`, `provider_reference_id`, `principal_amount`, `recipient_reference`, `corridor`, `destination_country`, `applied_fx_rate`, `completed_at`, `merchant_ref_id`, `fees[]` |
   | `disbursement.initiated` / `.failed` | **absent** | full records (see Phase 014 / task 001) |

   The chaos builders also derive a **single** fee from `gross − net`, but the ledger
   accepts an explicit **list** of `TransactionFeeLine` and the idea asks for a
   *dynamic fee form* ("the same fee account can be selected multiple times").

2. **`TransactionFeeLine` requires a `fee_code`** the chaos `FeeEntry` lacks, and the
   fee list must travel on the request — `PublishFlowRequest` has no fee transport.

3. **New input shapes** the descriptor model can't render: a repeatable fee sub-form, a
   supported-country dropdown, ULID-seeded references, a derived corridor, a nested
   `authorised_principal`, and VA pickers whose value goes to `flowFields` (not a slot)
   for the single-VA initiated/failed forms.

## Decision

### 1. A shared `TransactionFeeLine` model with `fee_code`, and `fees[]` on the request

Introduce one fee record reused by Collection and Disbursement-completed:

```java
@RecordBuilder
@JsonNaming(SnakeCaseStrategy.class)
public record TransactionFeeLine(
    String feeType,          // PLATFORM_FEE | PROVIDER_FEE (ledger TransactionFeeType)
    BigDecimal amount,
    String feeCode,          // required by the ledger
    String destinationVaId) {}
```

Add an optional, typed fee transport to `PublishFlowRequest`:

```java
List<FeeInput> fees   // FeeInput(feeType, amount, feeCode, destinationVaId); nullable/empty
```

Builders read `request.fees()` to populate the payload `fees[]` (and, for collection,
compute `gross = net + Σ amount` so both `gross_amount` and `net_amount` are emitted).
When `fees` is absent the builders preserve today's gross−net single-fee behaviour for
backward compatibility (CSV/batch).

### 2. Correct the inbound models/builders to the ledger as source of truth

Rewrite `CollectionCompletedEventData` + `CollectionFlowBuilder` and
`DisbursementCompletedEventData` + `DisbursementFlowBuilder`, and add
`DisbursementInitiatedEventData`/`DisbursementFailedEventData` + builders + the
`DISBURSEMENT_INITIATED`/`DISBURSEMENT_FAILED` flow types, all matching the field
tables in Phase 014 / task 001. Align `source()` strings to the samples
(`payment-service` for collection/disbursement; `settlements-service` for settlement).

**Settlement destination field correction (confirmed).** The destination field is
**`settlement_va_id`** (`settlementVaId`) — confirmed against the ledger. The chaos
`SettlementCompletedFlowBuilder` *and* `bin/kafka-payload-samples.md` currently use
**`destination_va_id`**, so today's settlement-completed flow is sending the wrong
destination name. The model/builder are corrected to emit `settlement_va_id` and to add
the missing `source_va_id`/`source_organization_id`/required `completion_reference`.

### 3. Descriptor extensions (additive to ADR-014)

| Addition | Where | Meaning |
|---|---|---|
| `FieldKind.FEE_LIST` | collection, disbursement-completed | repeatable fee rows → `request.fees[]`. Each row: `AMOUNT` + a **SYSTEM** VA picker (`destination_va_id`) + `autogen` `fee_code` + a fixed `fee_type` (`PLATFORM_FEE`/`PROVIDER_FEE`). |
| `FieldKind.COUNTRY` | disbursement `source_country` / `destination_country` | select whose options come from the **supported-countries** table (Phase 010), seeded `GH`. |
| `AutogenRule.ULID` | `merchant_ref_id`, `provider_reference_id`, `destination_bank_account`, `narration` | client + server mint a ULID (the project already has ULID ids in `base.Ids`). UUIDs stay `UUID_V4`. |
| Derived `corridor` | disbursement | computed `"{source_country}-{destination_country}"`, recomputed when either country changes; editable (advanced). |
| `authorised_principal` | disbursement-initiated | required nested `{ user_id, key_fingerprint }`; modeled as two advanced fields with defaults, assembled into the map by the builder. |
| `VA_REF` with `slotName = null` | settlement/disbursement initiated & failed | the picked VA id routes to **`flowFields[name]`** (e.g. `virtual_account_id`) instead of `slotOverrides`. `slotName != null` keeps today's slot routing. |

`SELECT` (existing) covers `disbursement_subtype` (`DOMESTIC` default,
`CROSS_BORDER`) and `destination_bank` (`ABSA` default). Ledger-required reference
fields that the idea marks non-required keep the Phase 011 pattern — `autogen` so a
collapsed form still validates, still editable/clearable for chaos: collection
`transaction_id` (UUID, the `transactionReference`); disbursement-initiated
`correlation_id`; disbursement-completed `provider_reference_id`; settlement-completed
`completion_reference`.

## Consequences

**Positive**
- The chaos machine finally emits the **real** Collection/Disbursement contracts;
  the long-standing "proposed disbursement contract" open question (ARCHITECTURE §10.4)
  is resolved against verified ledger source.
- One fee model + one descriptor kind drive both fee-bearing flows; the dynamic fee
  form is declarative catalog data, not bespoke component logic.
- All additions are backward-compatible: `fees` is optional (gross−net fallback
  retained), new kinds/rules extend existing enums, and the legacy
  `requiredFields`/`optionalFields`/`csvColumns` stay derived.

**Negative / trade-offs**
- Rewriting `CollectionCompletedEventData`/`DisbursementCompletedEventData` simply
  replaces hand-guessed fields that were already wrong (any prior consumer was
  mis-driving the ledger). The derived CSV columns update to the corrected fields; no
  careful migration is warranted (the harness drives a test ledger, not production
  consumers).
- The descriptor model grows (`FEE_LIST`/`COUNTRY`/`ULID`/derived/`flowFields`-VA),
  touching both the Java enums and the TS renderer. Mitigated by keeping each addition
  small and centrally mapped, as ADR-014 already does.
- The `settlement_va_id` correction fixes an already-wrong settlement-completed flow
  (it was emitting `destination_va_id`); covered by a builder test on the emitted key.
