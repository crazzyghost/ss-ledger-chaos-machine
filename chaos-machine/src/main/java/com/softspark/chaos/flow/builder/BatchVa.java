package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowContext;

/**
 * Package-private helper resolving the batch source {@code virtual_account_id} (and the Kafka
 * partition key) for the batch-item terminal builders.
 *
 * <p>The item terminal events carry only {@code virtual_account_id} (the batch source ORGANIZATION
 * VA, carried over from the reservation); they have no separate source/destination VA in the payload
 * (the ledger credits the destination from the batch's stored reservation). The value is read from
 * {@code flowFields} first and falls back to the resolved {@code source} slot.
 */
final class BatchVa {

  private BatchVa() {}

  /** Resolves the batch source VA: {@code flowFields[virtual_account_id]} then the {@code source} slot. */
  static String resolve(FlowFields f, FlowContext ctx) {
    String fromFields = f.getOptional("virtual_account_id");
    if (fromFields != null && !fromFields.isBlank()) {
      return fromFields;
    }
    return ctx.resolvedSlots().getOrDefault("source", "");
  }

  /** Partition key: the batch source VA, then the {@code source} slot, then {@code batch_id}, then the event id. */
  static String partitionKey(FlowContext ctx) {
    var fields = ctx.request().flowFields();
    Object vaId = fields.get("virtual_account_id");
    if (vaId != null && !vaId.toString().isBlank()) {
      return vaId.toString();
    }
    String sourceVa = ctx.resolvedSlots().get("source");
    if (sourceVa != null && !sourceVa.isBlank()) {
      return sourceVa;
    }
    return fields.getOrDefault("batch_id", ctx.eventId()).toString();
  }
}
