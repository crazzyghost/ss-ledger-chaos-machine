package com.softspark.chaos.flow.dto;

import com.softspark.chaos.flow.model.FlowType;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

/**
 * Catalog entry describing a single flow type and its required/optional field metadata.
 *
 * <p>Returned by {@code GET /api/v0/flows/catalog} to help callers construct valid flow requests.
 *
 * @param flowType the flow type
 * @param topic the Kafka topic this flow publishes to
 * @param source the source system identifier embedded in the event envelope
 * @param requiredFields list of required {@code flowFields} keys
 * @param optionalFields list of optional {@code flowFields} keys
 * @param csvColumns column names expected in a CSV batch file for this flow
 * @param partitionKeyField the {@code flowFields} key used as the Kafka partition key
 */
@RecordBuilder
public record FlowCatalogEntry(
    FlowType flowType,
    String topic,
    String source,
    List<String> requiredFields,
    List<String> optionalFields,
    List<String> csvColumns,
    String partitionKeyField) {}
