package com.softspark.chaos.account.controller;

import com.softspark.chaos.account.dto.FlowConfigResponse;
import com.softspark.chaos.account.dto.UpdateFlowConfigRequest;
import com.softspark.chaos.account.service.FlowConfigService;
import com.softspark.chaos.flow.model.FlowType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for flow configuration operations.
 * <p>
 * Provides endpoints for viewing and editing which account fills each slot of each transaction flow.
 */
@RestController
@RequestMapping("/api/v0/flow-configs")
@Tag(name = "Flow Configuration", description = "Transaction flow slot configuration")
public class FlowConfigController {

  private final FlowConfigService flowConfigService;

  public FlowConfigController(FlowConfigService flowConfigService) {
    this.flowConfigService = flowConfigService;
  }

  /**
   * Retrieves all flow configurations.
   *
   * @return a list of all flow configurations
   */
  @GetMapping
  @Operation(
      summary = "Get all flow configurations",
      description = "Retrieves all flow configurations showing which accounts fill each slot")
  public ResponseEntity<List<FlowConfigResponse>> getAllFlowConfigs() {
    var configs = flowConfigService.getAllFlowConfigs();
    return ResponseEntity.ok(configs);
  }

  /**
   * Retrieves flow configuration for a specific flow type.
   *
   * @param flowType the flow type
   * @return the flow configuration
   */
  @GetMapping("/{flowType}")
  @Operation(
      summary = "Get flow configuration for a specific flow type",
      description = "Retrieves the slot configuration for a specific transaction flow")
  public ResponseEntity<FlowConfigResponse> getFlowConfig(@PathVariable FlowType flowType) {
    var config = flowConfigService.getFlowConfig(flowType);
    return ResponseEntity.ok(config);
  }

  /**
   * Updates flow slot configuration for a specific flow type.
   *
   * @param flowType the flow type to update
   * @param request  the update request
   * @return the updated flow configuration
   */
  @PutMapping("/{flowType}")
  @Operation(
      summary = "Update flow configuration",
      description = "Updates the slot configuration for a specific transaction flow")
  public ResponseEntity<FlowConfigResponse> updateFlowConfig(
      @PathVariable FlowType flowType, @Valid @RequestBody UpdateFlowConfigRequest request) {
    var updated = flowConfigService.updateFlowConfig(flowType, request);
    return ResponseEntity.ok(updated);
  }
}
