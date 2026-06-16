package com.softspark.chaos.batch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.batch.dto.BatchRowResponse;
import com.softspark.chaos.batch.dto.BatchRunResponse;
import com.softspark.chaos.batch.service.BatchService;
import com.softspark.chaos.flow.chaos.ChaosOptions;
import com.softspark.chaos.flow.model.FlowType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for CSV batch flow publishing.
 */
@RestController
@RequestMapping("/api/v0/batches")
@Tag(name = "Batch", description = "CSV batch flow publishing")
public class BatchController {

  private static final Logger log = LoggerFactory.getLogger(BatchController.class);

  private final BatchService batchService;
  private final ObjectMapper objectMapper;

  public BatchController(BatchService batchService, ObjectMapper objectMapper) {
    this.batchService = batchService;
    this.objectMapper = objectMapper;
  }

  /**
   * Creates a new batch run from an uploaded CSV file.
   *
   * @param file the CSV file (multipart)
   * @param flowType the flow type for all rows in the CSV
   * @param maxRatePerSecond optional publish rate cap in events per second
   * @param chaosJson optional JSON-encoded {@link ChaosOptions}
   * @return {@code 202 Accepted} with the created batch run
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Create a batch run",
      description = "Uploads a CSV file and starts an asynchronous batch publishing run")
  public ResponseEntity<BatchRunResponse> createBatch(
      @RequestPart("file") MultipartFile file,
      @RequestParam FlowType flowType,
      @RequestParam(required = false) @Nullable Integer maxRatePerSecond,
      @RequestParam(required = false) @Nullable String chaosJson) {

    ChaosOptions chaos = parseChaosOptions(chaosJson);
    var response = batchService.createBatch(file, flowType, maxRatePerSecond, chaos);
    return ResponseEntity.accepted().body(response);
  }

  /**
   * Lists all batch runs, newest first.
   *
   * @param page zero-based page number
   * @param size page size
   * @return paginated list of batch runs
   */
  @GetMapping
  @Operation(summary = "List batch runs")
  public ResponseEntity<PageResponse<BatchRunResponse>> listBatches(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(batchService.listRuns(page, size));
  }

  /**
   * Retrieves a single batch run by id.
   *
   * @param id the batch run ULID
   * @return the batch run response
   */
  @GetMapping("/{id}")
  @Operation(summary = "Get batch run by id")
  public ResponseEntity<BatchRunResponse> getBatch(@PathVariable String id) {
    return ResponseEntity.ok(batchService.getRunById(id));
  }

  /**
   * Returns the rows for a batch run.
   *
   * @param id the batch run ULID
   * @param page zero-based page number
   * @param size page size
   * @return paginated list of batch rows
   */
  @GetMapping("/{id}/rows")
  @Operation(summary = "Get batch rows")
  public ResponseEntity<PageResponse<BatchRowResponse>> getRows(
      @PathVariable String id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(batchService.getRows(id, page, size));
  }

  @Nullable
  private ChaosOptions parseChaosOptions(@Nullable String chaosJson) {
    if (chaosJson == null || chaosJson.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(chaosJson, ChaosOptions.class);
    } catch (IOException e) {
      log.warn("Failed to parse chaosJson parameter: {}", e.getMessage());
      return null;
    }
  }
}
