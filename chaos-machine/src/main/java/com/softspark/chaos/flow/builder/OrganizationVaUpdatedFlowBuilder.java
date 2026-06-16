package com.softspark.chaos.flow.builder;

import com.softspark.chaos.flow.FlowBuilder;
import com.softspark.chaos.flow.FlowContext;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.flow.model.v1.OrganizationVaUpdatedEventData;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.TopicCatalog;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EventEnvelope} payloads for {@link FlowType#ORGANIZATION_VA_UPDATED} events.
 */
@Component
public class OrganizationVaUpdatedFlowBuilder
    implements FlowBuilder<OrganizationVaUpdatedEventData> {

  private final TopicCatalog topicCatalog;

  public OrganizationVaUpdatedFlowBuilder(TopicCatalog topicCatalog) {
    this.topicCatalog = topicCatalog;
  }

  @Override
  public FlowType type() {
    return FlowType.ORGANIZATION_VA_UPDATED;
  }

  @Override
  public String source() {
    return "organization-service";
  }

  @Override
  public EventEnvelope<OrganizationVaUpdatedEventData> build(FlowRequest request, FlowContext ctx) {
    var f = new FlowFields(request.flowFields());

    var data =
        new OrganizationVaUpdatedEventData(
            f.getRequired("id"),
            f.getRequired("status"),
            new OrganizationVaUpdatedEventData.CurrencyInfo(f.getRequired("currency_id")),
            new OrganizationVaUpdatedEventData.AccountType(f.getRequired("type_id")));

    String idempotencyKey = "organization-va-updated:" + ctx.eventId();
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
}
