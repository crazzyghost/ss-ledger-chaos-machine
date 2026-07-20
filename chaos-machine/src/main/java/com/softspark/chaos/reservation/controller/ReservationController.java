package com.softspark.chaos.reservation.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.reservation.dto.ReservationStateResponse;
import com.softspark.chaos.reservation.service.ReservationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API over the {@code reservation} projection (ADR-028): a per-VA list (the Reservations tab)
 * and a flat/batch query (the run-page toast watch + tracking). Distinct from the read-proxy at
 * {@code /api/v0/ledger/accounts/{id}/reservations}: this projection is push-fed and
 * event-faithful. AUTH-gated like every {@code /api/v0/**} route.
 */
@RestController
@Tag(name = "Reservations", description = "Ledger reservation lifecycle projection")
public class ReservationController {

  private final ReservationQueryService queryService;

  public ReservationController(ReservationQueryService queryService) {
    this.queryService = queryService;
  }

  /**
   * Reservations for a single virtual account.
   *
   * @param vaId the virtual account id
   * @param status optional status filter
   * @param from optional inclusive lower bound on {@code updated_at} (ISO-8601)
   * @param to optional inclusive upper bound on {@code updated_at} (ISO-8601)
   * @param page zero-based page (default 0)
   * @param size page size (default 20, max 100)
   * @return a page of reservations, newest first (empty if none)
   */
  @GetMapping("/api/v0/virtual-accounts/{vaId}/reservations")
  @Operation(summary = "Reservations for a virtual account")
  public ResponseEntity<PageResponse<ReservationStateResponse>> forVirtualAccount(
      @PathVariable String vaId,
      @RequestParam(required = false) @Nullable String status,
      @RequestParam(required = false) @Nullable Instant from,
      @RequestParam(required = false) @Nullable Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(queryService.forAccount(vaId, status, from, to, page, size));
  }

  /**
   * Flat/batch reservation query for the toast watch and general tracking.
   *
   * @param transactionRef filter by the inbound request id (the toast watch's primary key)
   * @param batchId filter by disbursement batch id
   * @param accountId repeatable account id parameter (1..50)
   * @param status optional status filter
   * @param page zero-based page (default 0)
   * @param size page size (default 20, max 100)
   * @return a page of reservations, newest first
   */
  @GetMapping("/api/v0/reservations")
  @Operation(summary = "Flat/batch reservation query")
  public ResponseEntity<PageResponse<ReservationStateResponse>> query(
      @RequestParam(required = false) @Nullable String transactionRef,
      @RequestParam(required = false) @Nullable String batchId,
      @RequestParam(required = false) @Nullable List<String> accountId,
      @RequestParam(required = false) @Nullable String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        queryService.query(transactionRef, batchId, accountId, status, page, size));
  }

  /**
   * Retrieves a single reservation by id (includes the raw payload).
   *
   * @param reservationId the reservation id
   * @return the reservation response
   */
  @GetMapping("/api/v0/reservations/{reservationId}")
  @Operation(summary = "Get reservation by id")
  public ResponseEntity<ReservationStateResponse> getById(@PathVariable String reservationId) {
    return ResponseEntity.ok(queryService.getById(reservationId));
  }
}
