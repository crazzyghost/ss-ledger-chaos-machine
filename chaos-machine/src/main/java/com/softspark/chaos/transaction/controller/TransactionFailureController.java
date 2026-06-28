package com.softspark.chaos.transaction.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.transaction.dto.TransactionFailureQuery;
import com.softspark.chaos.transaction.dto.TransactionFailureResponse;
import com.softspark.chaos.transaction.service.TransactionFailureQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API over the {@code transaction_failure} projection (ADR-025).
 *
 * <p>Serves three access shapes: a single {@code transactionRequestId} (the run-page poll), a batch
 * {@code transactionRequestIds} CSV (one call per "Sent" history page), and operator browse filters
 * — all paginated and ordered {@code occurred_at DESC}. AUTH-gated like every {@code /api/v0/**}
 * route.
 */
@RestController
@RequestMapping("/api/v0/transaction-failures")
@Tag(name = "Transaction Failures", description = "Ledger transaction-failure projection")
public class TransactionFailureController {

  private final TransactionFailureQueryService queryService;

  public TransactionFailureController(TransactionFailureQueryService queryService) {
    this.queryService = queryService;
  }

  /**
   * Lists transaction failures with optional filters, or resolves a batch of request ids.
   *
   * @param transactionRequestId single correlation-key lookup
   * @param transactionRequestIds CSV batch of correlation keys (takes precedence when present)
   * @param transactionType optional filter by transaction type
   * @param failureCode optional filter by failure code
   * @param ledgerTransactionId optional filter by the ledger recording id
   * @param from optional start of the occurred-at range (ISO-8601)
   * @param to optional end of the occurred-at range (ISO-8601)
   * @param page zero-based page number (default 0)
   * @param size page size (default 20, max 100)
   * @return paginated failures
   */
  @GetMapping
  @Operation(
      summary = "Query transaction failures",
      description =
          "Filter by single/batch transactionRequestId(s), type, failure code, ledger id, or time")
  public ResponseEntity<PageResponse<TransactionFailureResponse>> query(
      @RequestParam(required = false) @Nullable String transactionRequestId,
      @RequestParam(required = false) @Nullable List<String> transactionRequestIds,
      @RequestParam(required = false) @Nullable String transactionType,
      @RequestParam(required = false) @Nullable String failureCode,
      @RequestParam(required = false) @Nullable String ledgerTransactionId,
      @RequestParam(required = false) @Nullable Instant from,
      @RequestParam(required = false) @Nullable Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (transactionRequestIds != null && !transactionRequestIds.isEmpty()) {
      return ResponseEntity.ok(queryService.byRequestIds(transactionRequestIds));
    }
    var query =
        new TransactionFailureQuery(
            transactionRequestId,
            transactionType,
            failureCode,
            ledgerTransactionId,
            from,
            to,
            page,
            size);
    return ResponseEntity.ok(queryService.query(query));
  }

  /**
   * Retrieves a single transaction failure by id (includes the raw payload).
   *
   * @param id the projection row id
   * @return the failure response
   */
  @GetMapping("/{id}")
  @Operation(summary = "Get transaction failure by id")
  public ResponseEntity<TransactionFailureResponse> getById(@PathVariable String id) {
    return ResponseEntity.ok(queryService.getById(id));
  }
}
