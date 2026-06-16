package com.softspark.chaos.flow.controller;

import com.softspark.chaos.flow.FlowEngine;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.FlowResult;
import com.softspark.chaos.flow.builder.FlowCatalogProvider;
import com.softspark.chaos.flow.dto.FlowCatalogEntry;
import com.softspark.chaos.flow.dto.PublishFlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the transaction flow engine.
 *
 * <p>Provides a single publish endpoint and a catalog endpoint for discovery.
 */
@RestController
@RequestMapping("/api/v0/flows")
@Tag(name = "Flow Engine", description = "Transaction flow publishing")
public class FlowController {

  private final FlowEngine flowEngine;
  private final FlowCatalogProvider catalogProvider;

  public FlowController(FlowEngine flowEngine, FlowCatalogProvider catalogProvider) {
    this.flowEngine = flowEngine;
    this.catalogProvider = catalogProvider;
  }

  /**
   * Publishes a single transaction flow event.
   *
   * @param flowType the flow type to execute
   * @param request the publish request body
   * @return {@code 200} with the {@link FlowResult} on success; {@code 500} when publishing failed
   */
  @PostMapping("/{flowType}")
  @Operation(
      summary = "Publish a transaction flow event",
      description =
          "Resolves slots, builds an event envelope, optionally applies chaos, and "
              + "publishes to Kafka")
  public ResponseEntity<FlowResult> publish(
      @PathVariable FlowType flowType, @RequestBody PublishFlowRequest request) {

    Map<String, String> slotOverrides =
        request.slotOverrides() != null ? request.slotOverrides() : Map.of();
    Map<String, Object> flowFields = request.flowFields() != null ? request.flowFields() : Map.of();

    var flowRequest =
        FlowRequestBuilder.builder()
            .flowType(flowType)
            .correlationId(request.correlationId())
            .tenantId(request.tenantId())
            .channel(request.channel())
            .amount(request.amount())
            .grossAmount(request.grossAmount())
            .netAmount(request.netAmount())
            .currency(request.currency())
            .slotOverrides(slotOverrides)
            .chaos(request.chaos())
            .flowFields(flowFields)
            .build();

    FlowResult result = flowEngine.execute(flowRequest);
    return "PUBLISHED".equals(result.status())
        ? ResponseEntity.ok(result)
        : ResponseEntity.internalServerError().body(result);
  }

  /**
   * Returns the full flow catalog with required fields, optional fields, and CSV column metadata.
   *
   * @return {@code 200} with the list of catalog entries
   */
  @GetMapping("/catalog")
  @Operation(
      summary = "Get the flow catalog",
      description = "Lists all supported flow types with their required and optional fields")
  public ResponseEntity<List<FlowCatalogEntry>> catalog() {
    return ResponseEntity.ok(catalogProvider.catalog());
  }
}
