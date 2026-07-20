package com.softspark.chaos.consistencycheck.controller;

import com.softspark.chaos.consistencycheck.dto.ReconciliationMismatchPollResponse;
import com.softspark.chaos.consistencycheck.service.ReconciliationMismatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for polling reconciliation mismatches.
 *
 * <p>Supports the chaos UI's toast-notification use case: {@code GET
 * /reconciliation-mismatches?since=} returns all mismatches consumed after {@code since}, ordered
 * ascending by {@code consumedAt}.
 */
@RestController
@RequestMapping("/api/v0/reconciliation-mismatches")
@Tag(
    name = "Reconciliation Mismatches",
    description = "Polling endpoint for reconciliation mismatch events (toast notification)")
@SecurityRequirement(name = "bearerAuth")
public class ReconciliationMismatchController {

  private final ReconciliationMismatchService service;

  public ReconciliationMismatchController(ReconciliationMismatchService service) {
    this.service = service;
  }

  /**
   * Polls for reconciliation mismatches consumed after a given timestamp.
   *
   * <p>The UI polls this endpoint every 5s when the consistency checks page is open or within 60s
   * of triggering a check. The {@code nextSince} cursor from the previous response is used as the
   * {@code since} parameter for the next poll.
   *
   * @param since the exclusive lower bound (ISO-8601 LocalDateTime, no zone)
   * @param size the page size (default 20, max 100)
   * @return a page of mismatches with the nextSince cursor
   */
  @GetMapping
  @Operation(
      summary = "Poll for reconciliation mismatches",
      description =
          "Returns all mismatches consumed after the given timestamp, ordered ascending. Use the"
              + " nextSince cursor for the next poll.")
  public ResponseEntity<ReconciliationMismatchPollResponse> pollMismatches(
      @RequestParam String since, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    var sinceTimestamp = LocalDateTime.parse(since);
    var response = service.pollMismatches(sinceTimestamp, size);
    return ResponseEntity.ok(response);
  }
}
