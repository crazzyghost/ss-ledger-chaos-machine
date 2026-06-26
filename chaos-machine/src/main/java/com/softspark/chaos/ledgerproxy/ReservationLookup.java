package com.softspark.chaos.ledgerproxy;

import com.softspark.chaos.ledgerproxy.dto.ReservationResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the ledger-created {@code reservation_id} for a disbursement by polling the reservations
 * read-proxy ({@link LedgerClient#getReservations}) until the reservation appears or a timeout
 * elapses (ADR-018).
 *
 * <p>Shared by both consumers: the interactive wizard polls via HTTP, while the RANDOM lifecycle
 * runner calls {@link #find} in-process between the initiated and completed/failed publishes. The
 * poll is bounded (interval + total timeout) so a never-arriving reservation can never hang a worker;
 * on timeout the caller falls back to a manual entry (interactive) or an autogen placeholder
 * (unattended).
 */
@Component
public class ReservationLookup {

  private static final Logger log = LoggerFactory.getLogger(ReservationLookup.class);

  private final LedgerClient ledgerClient;
  private final long pollIntervalMs;
  private final long defaultTimeoutMs;

  public ReservationLookup(
      LedgerClient ledgerClient,
      @Value("${chaos.ledger.reservation.poll.interval-ms:500}") long pollIntervalMs,
      @Value("${chaos.ledger.reservation.poll.timeout-ms:5000}") long defaultTimeoutMs) {
    this.ledgerClient = ledgerClient;
    this.pollIntervalMs = pollIntervalMs;
    this.defaultTimeoutMs = defaultTimeoutMs;
  }

  /**
   * Polls for the reservation keyed by {@code transactionRef} on the given account, using the
   * configured default timeout.
   *
   * @param callerToken the caller's bearer token (empty/service token for in-process callers)
   * @param accountId the ledger account UUID (the disbursement's org VA)
   * @param transactionRef the transaction reference (= the disbursement {@code transaction_id})
   * @return the resolved {@code reservation_id}, or empty on timeout
   */
  public Optional<String> find(String callerToken, String accountId, String transactionRef) {
    return find(callerToken, accountId, transactionRef, Duration.ofMillis(defaultTimeoutMs));
  }

  /**
   * Polls for the reservation keyed by {@code transactionRef} on the given account until it appears
   * or {@code timeout} elapses, on a bounded, interrupt-aware interval.
   *
   * @param callerToken the caller's bearer token
   * @param accountId the ledger account UUID
   * @param transactionRef the transaction reference to match
   * @param timeout the total poll budget
   * @return the resolved {@code reservation_id} (first match), or empty on timeout/interrupt
   */
  public Optional<String> find(
      String callerToken, String accountId, String transactionRef, Duration timeout) {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (true) {
      Optional<String> hit = tryFind(callerToken, accountId, transactionRef);
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

  /** The configured default timeout in milliseconds. */
  public long defaultTimeoutMs() {
    return defaultTimeoutMs;
  }

  private Optional<String> tryFind(String callerToken, String accountId, String transactionRef) {
    try {
      List<ReservationResponse> hits =
          ledgerClient.getReservations(callerToken, accountId, transactionRef);
      return hits.isEmpty() ? Optional.empty() : Optional.ofNullable(hits.get(0).id());
    } catch (RuntimeException e) {
      // A slow/unreachable ledger should not abort the poll — retry until the deadline.
      log.warn(
          "Reservation lookup attempt failed for account {} ref {}: {}",
          accountId,
          transactionRef,
          e.getMessage());
      return Optional.empty();
    }
  }
}
