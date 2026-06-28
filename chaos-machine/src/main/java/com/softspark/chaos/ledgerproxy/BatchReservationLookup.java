package com.softspark.chaos.ledgerproxy;

import com.softspark.chaos.ledgerproxy.dto.DisbursementBatchSummaryDto;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the ledger-created BATCH {@code reservation_id} for a batch disbursement by polling the
 * batch-summary read-proxy ({@link LedgerClient#getDisbursementBatch}) until the reservation appears
 * or a timeout elapses (ADR-023), and exposes a single-read accessor for progress/finalize.
 *
 * <p>Sibling to {@link ReservationLookup} (single-flow, account-reservations keyed by
 * {@code transaction_id}); this one keys off {@code batch_id} against the batch-summary endpoint and
 * additionally surfaces the live batch status + counters. Shared by both consumers: the interactive
 * wizard polls via HTTP, while the automatic runner calls {@link #find} in-process between the
 * reservation publish and the item loop. The poll is bounded (interval + total timeout) so a
 * never-arriving reservation can never hang a worker; on timeout the caller falls back to a manual
 * entry (interactive) or an autogen placeholder (unattended). It reuses the same
 * {@code chaos.ledger.reservation.poll.*} config as {@link ReservationLookup}.
 */
@Component
public class BatchReservationLookup {

  private static final Logger log = LoggerFactory.getLogger(BatchReservationLookup.class);

  private final LedgerClient ledgerClient;
  private final long pollIntervalMs;
  private final long defaultTimeoutMs;

  public BatchReservationLookup(
      LedgerClient ledgerClient,
      @Value("${chaos.ledger.reservation.poll.interval-ms:500}") long pollIntervalMs,
      @Value("${chaos.ledger.reservation.poll.timeout-ms:5000}") long defaultTimeoutMs) {
    this.ledgerClient = ledgerClient;
    this.pollIntervalMs = pollIntervalMs;
    this.defaultTimeoutMs = defaultTimeoutMs;
  }

  /**
   * Polls for the BATCH reservation id of the given batch, using the configured default timeout.
   *
   * @param callerToken the caller's bearer token (empty/service token for in-process callers)
   * @param batchId the batch id (the driver-controlled {@code batch_id})
   * @return the resolved {@code reservation_id}, or empty on timeout
   */
  public Optional<String> find(String callerToken, String batchId) {
    return find(callerToken, batchId, Duration.ofMillis(defaultTimeoutMs));
  }

  /**
   * Polls for the BATCH reservation id of the given batch until it appears or {@code timeout}
   * elapses, on a bounded, interrupt-aware interval.
   *
   * @param callerToken the caller's bearer token
   * @param batchId the batch id
   * @param timeout the total poll budget
   * @return the resolved {@code reservation_id}, or empty on timeout/interrupt
   */
  public Optional<String> find(String callerToken, String batchId, Duration timeout) {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (true) {
      Optional<String> hit = tryFind(callerToken, batchId);
      if (hit.isPresent()) {
        return hit;
      }
      if (System.nanoTime() >= deadlineNanos) {
        return Optional.empty();
      }
      try {
        Thread.sleep(pollIntervalMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Optional.empty();
      }
    }
  }

  /**
   * Reads the latest batch summary in a single call (no polling), for progress display or
   * finalize-time stamping. Returns empty if the ledger read fails.
   *
   * @param callerToken the caller's bearer token
   * @param batchId the batch id
   * @return the latest summary, or empty on a ledger read failure
   */
  public Optional<DisbursementBatchSummaryDto> summary(String callerToken, String batchId) {
    try {
      return Optional.ofNullable(ledgerClient.getDisbursementBatch(callerToken, batchId));
    } catch (RuntimeException e) {
      log.warn("Batch summary read failed for batch {}: {}", batchId, e.getMessage());
      return Optional.empty();
    }
  }

  /** The configured default timeout in milliseconds. */
  public long defaultTimeoutMs() {
    return defaultTimeoutMs;
  }

  private Optional<String> tryFind(String callerToken, String batchId) {
    try {
      DisbursementBatchSummaryDto summary = ledgerClient.getDisbursementBatch(callerToken, batchId);
      if (summary == null) {
        return Optional.empty();
      }
      String reservationId = summary.reservationId();
      return reservationId != null && !reservationId.isBlank()
          ? Optional.of(reservationId)
          : Optional.empty();
    } catch (RuntimeException e) {
      // A slow/unreachable ledger should not abort the poll — retry until the deadline.
      log.warn("Batch reservation lookup attempt failed for batch {}: {}", batchId, e.getMessage());
      return Optional.empty();
    }
  }
}
