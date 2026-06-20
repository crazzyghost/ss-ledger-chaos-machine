package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.OrganizationOnboardedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#ORGANIZATION_ONBOARDED} events.
 */
@Component
public class OrganizationOnboardedFlowBuilder
    implements FlowBuilder<OrganizationOnboardedEventData> {

  private final TopicCatalog topicCatalog;

  public OrganizationOnboardedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.ORGANIZATION_ONBOARDED;
  }

  @Override
  public String source() {
    return "organization-service";
  }

  @Override
  public EventEnvelope<OrganizationOnboardedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());

    var orgType =
        new OrganizationOnboardedEventData.OrganizationType(
            f.getRequired("type_id"), f.getRequired("type_name"));
    var country =
        new OrganizationOnboardedEventData.Country(
            f.getRequired("country_id"),
            f.getRequired("country_name"),
            f.getRequired("iso_code"),
            f.getOptional("country_status"),
            parseInstant(f.getOptional("country_modified_date")));

    var data =
        new OrganizationOnboardedEventData(
            f.getRequired("id"),
            f.getRequired("name"),
            orgType,
            country,
            f.getOptional("primary_contact_email"),
            List.of(),
            f.getRequired("status"));

    String idempotencyKey = "organization-onboarded:" + ctx.eventId();
    var metadata = new EventMetadata(ctx.correlationId(), idempotencyKey, ctx.tenantId());

    return new EventEnvelope<>(
        ctx.eventId(),
        topicCatalog.topicFor(type()),
        ctx.timestamp(),
        source(),
        "1.0",
        data,
        metadata);
  }

  @Override
  public String partitionKey(FlowContext ctx) {
    String id = ctx.request().flowFields().getOrDefault("id", ctx.eventId()).toString();
    return id;
  }

  private static Instant parseInstant(String value) {
    return value != null && !value.isBlank() ? Instant.parse(value) : null;
  }
}
