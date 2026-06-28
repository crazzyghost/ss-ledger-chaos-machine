package com.softspark.chaos.reservation.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.reservation.dto.ReservationStateResponse;
import com.softspark.chaos.reservation.model.Reservation;
import com.softspark.chaos.reservation.repository.ReservationRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service over the {@code reservation} projection (ADR-028). Newest-first by {@code updated_at
 * DESC}. The flat endpoint dispatches by the most specific present filter (same style as {@code
 * HistoryQueryService}); the per-VA endpoint composes account + status/time.
 */
@Service
public class ReservationQueryService {

  /** Upper bound on the flat/batch {@code accountId} cardinality. */
  static final int MAX_ACCOUNT_BATCH = 50;

  private static final int MAX_PAGE_SIZE = 100;
  private static final Sort UPDATED_DESC = Sort.by(Sort.Direction.DESC, "updatedAt");

  private final ReservationRepository repository;

  public ReservationQueryService(ReservationRepository repository) {
    this.repository = repository;
  }

  /**
   * Reservations for a single VA (the Reservations tab), optionally filtered by status or time.
   *
   * @param accountId the VA id
   * @param status optional status filter
   * @param from optional inclusive lower bound on {@code updated_at}
   * @param to optional inclusive upper bound on {@code updated_at}
   * @param page zero-based page
   * @param size page size (clamped to 100)
   * @return a page of reservations, newest first
   */
  @Transactional(readOnly = true)
  public PageResponse<ReservationStateResponse> forAccount(
      String accountId,
      @Nullable String status,
      @Nullable Instant from,
      @Nullable Instant to,
      int page,
      int size) {
    var pageable = PageRequest.of(Math.max(page, 0), clampSize(size), UPDATED_DESC);
    // Account scope + optional status/time all AND-compose; single time bounds honoured.
    Specification<Reservation> spec =
        (root, criteria, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(cb.equal(root.get("accountId"), accountId));
          if (status != null && !status.isBlank()) {
            predicates.add(cb.equal(root.get("status"), status));
          }
          if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("updatedAt"), from));
          }
          if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("updatedAt"), to));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };
    return toPage(repository.findAll(spec, pageable));
  }

  /**
   * Flat/batch reservation query for the toast watch and general tracking. Dispatches by the most
   * specific present filter: {@code transactionRef} → {@code batchId} → {@code accountId} (batch) →
   * {@code status} → all.
   *
   * @param transactionRef the inbound request id (the toast watch's primary key)
   * @param batchId the disbursement batch id
   * @param accountIds repeatable account ids (1..50 when used)
   * @param status optional status filter
   * @param page zero-based page
   * @param size page size (clamped to 100)
   * @return a page of reservations, newest first
   */
  @Transactional(readOnly = true)
  public PageResponse<ReservationStateResponse> query(
      @Nullable String transactionRef,
      @Nullable String batchId,
      @Nullable List<String> accountIds,
      @Nullable String status,
      int page,
      int size) {
    var pageable = PageRequest.of(Math.max(page, 0), clampSize(size), UPDATED_DESC);

    if (transactionRef != null && !transactionRef.isBlank()) {
      var rows =
          repository.findByTransactionId(transactionRef).stream()
              .filter(r -> status == null || status.isBlank() || status.equalsIgnoreCase(r.getStatus()))
              .sorted(Comparator.comparing(Reservation::getUpdatedAt).reversed())
              .map(ReservationStateResponse::from)
              .toList();
      return new PageResponse<>(rows, 0, rows.size(), rows.size());
    }
    if (batchId != null && !batchId.isBlank()) {
      return toPage(repository.findByDisbursementBatchId(batchId, pageable));
    }
    List<String> ids = sanitize(accountIds);
    if (!ids.isEmpty()) {
      if (ids.size() > MAX_ACCOUNT_BATCH) {
        throw new BadRequestException("accountId exceeds the maximum of " + MAX_ACCOUNT_BATCH);
      }
      return toPage(repository.findByAccountIdIn(ids, pageable));
    }
    if (status != null && !status.isBlank()) {
      return toPage(repository.findByStatus(status, pageable));
    }
    return toPage(repository.findAll(pageable));
  }

  /**
   * Fetches a single reservation by id.
   *
   * @param reservationId the reservation id
   * @return the response
   * @throws NotFoundException if absent
   */
  @Transactional(readOnly = true)
  public ReservationStateResponse getById(String reservationId) {
    return repository
        .findById(reservationId)
        .map(ReservationStateResponse::from)
        .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
  }

  private static List<String> sanitize(List<String> raw) {
    return raw == null
        ? List.of()
        : raw.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();
  }

  private static int clampSize(int size) {
    return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
  }

  private static PageResponse<ReservationStateResponse> toPage(Page<Reservation> page) {
    var items = page.getContent().stream().map(ReservationStateResponse::from).toList();
    return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
  }
}
