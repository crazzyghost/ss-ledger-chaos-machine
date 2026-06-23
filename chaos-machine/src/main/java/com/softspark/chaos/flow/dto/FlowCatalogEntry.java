package com.softspark.chaos.flow.dto;

import com.softspark.chaos.flow.model.FlowType;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

/**
 * Catalog entry describing a single flow type and its field metadata.
 *
 * <p>Returned by {@code GET /api/v0/flows/catalog} to help callers construct valid flow requests.
 * The {@code fields} descriptors are the source of truth for the Single Flow Run form;
 * {@code requiredFields}/{@code optionalFields}/{@code csvColumns} remain for legacy consumers (the
 * CSV batch page) and are unchanged.
 *
 * @param flowType the flow type
 * @param topic the Kafka topic this flow publishes to
 * @param source the source system identifier embedded in the event envelope
 * @param runnerVisible whether the Single Flow Run page exposes this flow as a transaction type
 * @param fields structured per-field descriptors driving the redesigned form
 * @param requiredFields list of required {@code flowFields} keys (legacy)
 * @param optionalFields list of optional {@code flowFields} keys (legacy)
 * @param csvColumns column names expected in a CSV batch file for this flow
 * @param partitionKeyField the {@code flowFields} key used as the Kafka partition key
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
    String partitionKeyField) {}
