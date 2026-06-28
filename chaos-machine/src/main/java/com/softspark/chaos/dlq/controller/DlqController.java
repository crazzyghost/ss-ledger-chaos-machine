package com.softspark.chaos.dlq.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.dlq.dto.DeadLetterRecordResponse;
import com.softspark.chaos.dlq.service.DlqQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API over the {@code dlq} projection (ADR-029): a filterable list of dead-lettered messages
 * and a by-id detail (including the original payload + raw DLT JSON). AUTH-gated like every {@code
 * /api/v0/**} route.
 */
@RestController
@RequestMapping("/api/v0/dlq")
@Tag(name = "Dead Letter Queue", description = "Ledger inbound dead-letter projection")
public class DlqController {

  private final DlqQueryService queryService;

  public DlqController(DlqQueryService queryService) {
    this.queryService = queryService;
  }

  /**
   * Lists dead letters with optional filters, newest first.
   *
   * @param domain optional domain filter
   * @param transactionId optional transaction id filter
   * @param transactionType optional transaction type filter
   * @param originalTopic optional original-topic filter
   * @param failureClassification optional classification filter
   * @param from optional inclusive lower bound on {@code received_at} (ISO-8601)
   * @param to optional inclusive upper bound on {@code received_at} (ISO-8601)
   * @param page zero-based page (default 0)
   * @param size page size (default 20, max 100)
   * @return a page of dead-letter summaries
   */
  @GetMapping
  @Operation(summary = "Query the dead letter queue")
  public ResponseEntity<PageResponse<DeadLetterRecordResponse>> list(
      @RequestParam(required = false) @Nullable String domain,
      @RequestParam(required = false) @Nullable String transactionId,
      @RequestParam(required = false) @Nullable String transactionType,
      @RequestParam(required = false) @Nullable String originalTopic,
      @RequestParam(required = false) @Nullable String failureClassification,
      @RequestParam(required = false) @Nullable Instant from,
      @RequestParam(required = false) @Nullable Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        queryService.list(
            domain, transactionId, transactionType, originalTopic, failureClassification, from, to,
            page, size));
  }

  /**
   * Retrieves a single dead letter by id, including the original payload + raw DLT JSON.
   *
   * @param id the projection row id
   * @return the detail response
   */
  @GetMapping("/{id}")
  @Operation(summary = "Get a dead letter by id")
  public ResponseEntity<DeadLetterRecordResponse> getById(@PathVariable String id) {
    return ResponseEntity.ok(queryService.getById(id));
  }
}
