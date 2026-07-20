package com.softspark.chaos.consistencycheck.controller;

import com.softspark.chaos.consistencycheck.dto.ConsistencyCheckDiscrepancyListResponse;
import com.softspark.chaos.consistencycheck.dto.ConsistencyCheckListResponse;
import com.softspark.chaos.consistencycheck.dto.ConsistencyCheckResponse;
import com.softspark.chaos.consistencycheck.dto.ConsistencyCheckTriggerResponse;
import com.softspark.chaos.exception.InternalServerErrorException;
import com.softspark.chaos.exception.UnauthorizedException;
import com.softspark.chaos.ledgerproxy.LedgerClient;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreakerOpenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxy controller for the ledger's consistency-check API.
 *
 * <p>This controller is a thin proxy that forwards all calls to {@link LedgerClient} and maps the
 * ledger's DTOs to chaos-facing DTOs. It does not validate or recompute anything locally; all
 * business logic lives in the ledger. Follows ADR-036 (faithful status propagation).
 *
 * <p>All four endpoints require authentication and forward the operator's bearer token to the
 * ledger. Circuit breaker guards all calls; when open, all endpoints return {@code 503}.
 */
@RestController
@RequestMapping("/api/v0/ledger/consistency-checks")
@Tag(
    name = "Consistency Checks (Ledger Proxy)",
    description = "Proxied consistency-check endpoints (trigger, list, get, list discrepancies)")
@SecurityRequirement(name = "bearerAuth")
public class ConsistencyCheckProxyController {

  private final LedgerClient ledgerClient;

  /**
   * Constructs the controller.
   *
   * @param ledgerClient the ledger client
   */
  public ConsistencyCheckProxyController(LedgerClient ledgerClient) {
    this.ledgerClient = ledgerClient;
  }

  /**
   * Triggers one or all consistency checks on the ledger.
   *
   * <p>Proxies {@code PUT /api/v0/consistency-checks?type=}. Returns {@code 201} with the list of
   * triggered checks (one or three, depending on {@code type}).
   *
   * @param type the check type ({@code ALL}, {@code ACCOUNT_BALANCE_PROJECTION}, {@code
   *     ENTRY_BALANCE}, {@code SEQUENCE_INTEGRITY}), or {@code null} (defaults to {@code ALL})
   * @param request the HTTP request (for token extraction)
   * @return {@code 201} with the trigger response
   */
  @PutMapping
  @Operation(
      summary = "Trigger consistency check",
      description =
          "Triggers one or all consistency checks. Each check is processed asynchronously by the"
              + " ledger's task queue. Poll the check status or wait for the mismatch event.")
  public ResponseEntity<ConsistencyCheckTriggerResponse> triggerConsistencyCheck(
      @RequestParam(required = false) @Nullable String type, HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var ledgerResponse = ledgerClient.triggerConsistencyChecks(token, type);
      var response = ConsistencyCheckTriggerResponse.from(ledgerResponse);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Lists consistency checks from the ledger with optional filters.
   *
   * <p>Proxies {@code GET /api/v0/consistency-checks?type=&status=&initiatorType=&page=&size=}.
   *
   * @param type optional filter by check type
   * @param status optional filter by status
   * @param initiatorType optional filter by initiator type
   * @param page zero-based page number
   * @param size page size
   * @param request the HTTP request (for token extraction)
   * @return a paginated list of consistency checks
   */
  @GetMapping
  @Operation(
      summary = "List consistency checks",
      description = "Lists consistency checks with optional filters, paginated, newest first.")
  public ResponseEntity<ConsistencyCheckListResponse> listConsistencyChecks(
      @RequestParam(required = false) @Nullable String type,
      @RequestParam(required = false) @Nullable String status,
      @RequestParam(required = false) @Nullable String initiatorType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var ledgerResponse =
          ledgerClient.listConsistencyChecks(token, type, status, initiatorType, page, size);
      var response = ConsistencyCheckListResponse.fromPage(ledgerResponse);
      return ResponseEntity.ok(response);
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Retrieves a single consistency check from the ledger.
   *
   * <p>Proxies {@code GET /api/v0/consistency-checks/{checkId}}.
   *
   * @param checkId the check ID
   * @param request the HTTP request (for token extraction)
   * @return the consistency check
   */
  @GetMapping("/{checkId}")
  @Operation(
      summary = "Get consistency check",
      description = "Retrieves a single consistency check by ID.")
  public ResponseEntity<ConsistencyCheckResponse> getConsistencyCheck(
      @PathVariable String checkId, HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var ledgerResponse = ledgerClient.getConsistencyCheck(token, checkId);
      var response = ConsistencyCheckResponse.from(ledgerResponse);
      return ResponseEntity.ok(response);
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Lists discrepancies (findings) for a single consistency check.
   *
   * <p>Proxies {@code GET /api/v0/consistency-checks/{checkId}/discrepancies?code=&page=&size=}.
   *
   * @param checkId the check ID
   * @param code optional filter by discrepancy code
   * @param page zero-based page number
   * @param size page size
   * @param request the HTTP request (for token extraction)
   * @return a paginated list of discrepancies
   */
  @GetMapping("/{checkId}/discrepancies")
  @Operation(
      summary = "List consistency check discrepancies",
      description = "Lists discrepancies (findings) for a single consistency check, paginated.")
  public ResponseEntity<ConsistencyCheckDiscrepancyListResponse> listConsistencyCheckDiscrepancies(
      @PathVariable String checkId,
      @RequestParam(required = false) @Nullable String code,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var ledgerResponse =
          ledgerClient.listConsistencyCheckDiscrepancies(token, checkId, code, page, size);
      var response = ConsistencyCheckDiscrepancyListResponse.fromPage(ledgerResponse);
      return ResponseEntity.ok(response);
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Cancels a running or pending consistency check.
   *
   * <p>Proxies {@code DELETE /api/v0/consistency-checks/{checkId}}. The ledger will attempt to
   * cancel the check if it is still {@code PENDING} or {@code IN_PROGRESS}. If the check has
   * already completed or failed, the ledger returns an appropriate error status.
   *
   * @param checkId the check ID to cancel
   * @param request the HTTP request (for token extraction)
   * @return 204 No Content on successful cancellation
   */
  @DeleteMapping("/{checkId}")
  @Operation(
      summary = "Cancel consistency check",
      description =
          "Cancels a running or pending consistency check. Returns 204 on success, 4xx if the check"
              + " cannot be cancelled.")
  public ResponseEntity<Void> cancelConsistencyCheck(
      @PathVariable String checkId, HttpServletRequest request) {
    var token = extractToken(request);
    try {
      ledgerClient.cancelConsistencyCheck(token, checkId);
      return ResponseEntity.noContent().build();
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  private static String extractToken(HttpServletRequest request) {
    var header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    throw new UnauthorizedException("Missing or invalid Authorization header");
  }
}
