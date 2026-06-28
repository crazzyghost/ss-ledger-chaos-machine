package com.softspark.chaos.transaction.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.transaction.dto.TransactionFailureQuery;
import com.softspark.chaos.transaction.dto.TransactionFailureResponse;
import com.softspark.chaos.transaction.model.TransactionFailure;
import com.softspark.chaos.transaction.repository.TransactionFailureRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service over the {@code transaction_failure} projection (ADR-025 query surface).
 *
 * <p>Dispatches by which filter is present (same style as {@code HistoryQueryService}), defaulting
 * to {@code occurred_at DESC}. The batch path resolves outcomes for a whole "Sent" history page in
 * one bounded {@code IN (…)} query.
 */
@Service
public class TransactionFailureQueryService {

  /** Upper bound on a single batch {@code transactionRequestIds} lookup. */
  static final int MAX_REQUEST_ID_BATCH = 200;

  private static final Sort OCCURRED_DESC = Sort.by(Sort.Direction.DESC, "occurredAt");

  private final TransactionFailureRepository repository;

  public TransactionFailureQueryService(TransactionFailureRepository repository) {
    this.repository = repository;
  }

  /**
   * Browse/single-filter query.
   *
   * @param query the filters
   * @return a page of matching failures, newest first
   */
  @Transactional(readOnly = true)
  public PageResponse<TransactionFailureResponse> query(TransactionFailureQuery query) {
    var pageable = PageRequest.of(query.page(), query.size(), OCCURRED_DESC);
    // All present filters AND-compose; single time bounds are honoured independently.
    Specification<TransactionFailure> spec =
        (root, criteria, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (query.transactionRequestId() != null) {
            predicates.add(cb.equal(root.get("transactionRequestId"), query.transactionRequestId()));
          }
          if (query.transactionType() != null) {
            predicates.add(cb.equal(root.get("transactionType"), query.transactionType()));
          }
          if (query.failureCode() != null) {
            predicates.add(cb.equal(root.get("failureCode"), query.failureCode()));
          }
          if (query.ledgerTransactionId() != null) {
            predicates.add(cb.equal(root.get("ledgerTransactionId"), query.ledgerTransactionId()));
          }
          if (query.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), query.from()));
          }
          if (query.to() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("occurredAt"), query.to()));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };
    var page = repository.findAll(spec, pageable);
    var items = page.getContent().stream().map(TransactionFailureResponse::from).toList();
    return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
  }

  /**
   * Batch lookup of failures by a set of request ids (one call per "Sent" history page).
   *
   * @param rawIds the request ids (may contain blanks/duplicates)
   * @return a single page containing every matching failure, newest first
   * @throws BadRequestException if the id set is empty or exceeds {@link #MAX_REQUEST_ID_BATCH}
   */
  @Transactional(readOnly = true)
  public PageResponse<TransactionFailureResponse> byRequestIds(List<String> rawIds) {
    List<String> ids =
        rawIds == null
            ? List.of()
            : rawIds.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    if (ids.isEmpty()) {
      throw new BadRequestException("transactionRequestIds must contain at least one id");
    }
    if (ids.size() > MAX_REQUEST_ID_BATCH) {
      throw new BadRequestException(
          "transactionRequestIds exceeds the maximum of " + MAX_REQUEST_ID_BATCH);
    }
    var items =
        repository.findByTransactionRequestIdIn(ids).stream()
            .sorted(Comparator.comparing(TransactionFailure::getOccurredAt).reversed())
            .map(TransactionFailureResponse::from)
            .toList();
    return new PageResponse<>(items, 0, items.size(), items.size());
  }

  /**
   * Fetches a single failure by id.
   *
   * @param id the projection row id
   * @return the response
   * @throws NotFoundException if absent
   */
  @Transactional(readOnly = true)
  public TransactionFailureResponse getById(String id) {
    return repository
        .findById(id)
        .map(TransactionFailureResponse::from)
        .orElseThrow(() -> new NotFoundException("Transaction failure not found: " + id));
  }
}
