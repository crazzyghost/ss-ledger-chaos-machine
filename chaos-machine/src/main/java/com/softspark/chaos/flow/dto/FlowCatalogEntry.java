package com.softspark.chaos.flow.dto;

import com.softspark.chaos.flow.model.FlowType;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

/**
 * Catalog entry describing a single flow type and its field metadata.
 *
 * <p>Returned by {@code GET /api/v0/flows/catalog} to help callers construct valid flow requests.
 * The {@code fields} descriptors are the source of truth for the Single Flow Run form;
 * {@code requiredFields}/{@code optionalFields} remain for legacy consumers.
 *
 * <p><strong>Vestigial:</strong> {@code csvColumns} is retained but no longer read by anything — the
 * CSV-batch ingest path (its only consumer, {@code CsvFlowParser}) was retired in Phase 021
 * (ADR-031). It is kept on the record only to avoid stripping a positional argument from every
 * catalog call site; it may be removed wholesale in a future cleanup.
 *
 * @param flowType the flow type
 * @param topic the Kafka topic this flow publishes to
 * @param source the source system identifier embedded in the event envelope
 * @param runnerVisible whether the Single Flow Run page exposes this flow as a transaction type
 * @param fields structured per-field descriptors driving the redesigned form
 * @param requiredFields list of required {@code flowFields} keys (legacy)
 * @param optionalFields list of optional {@code flowFields} keys (legacy)
 * @param csvColumns vestigial: CSV retired (Phase 021); no longer read
 * @param partitionKeyField the {@code flowFields} key used as the Kafka partition key
 * @param lifecycle the lifecycle grouping for a multi-step transaction type (set on the
 *     {@code initiated} entry); {@code null} for single-shot flows
 * @param batchGroup the batch-disbursement fan-out grouping (set on the reservation entry);
 *     {@code null} otherwise. Mutually exclusive with {@code lifecycle} — at most one is non-null.
 */
@RecordBuilder
public record FlowCatalogEntry(
    FlowType flowType,
    String topic,
    String source,
    boolean runnerVisible,
    List<FlowFieldDescriptor> fields,
    List<String> requiredFields,
    List<String> optionalFields,
    List<String> csvColumns,
    String partitionKeyField,
    FlowLifecycle lifecycle,
    BatchDisbursementGroup batchGroup) {}
