package com.softspark.chaos.dlq.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.dlq.dto.DeadLetterRecordResponse;
import com.softspark.chaos.dlq.model.DeadLetterRecord;
import com.softspark.chaos.dlq.repository.DeadLetterRecordRepository;
import com.softspark.chaos.exception.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service over the {@code dlq} projection (ADR-029). Newest-first by {@code received_at DESC};
 * list rows omit the heavy payload columns (returned only by {@link #getById(String)}). Dispatches
 * by the most specific present filter (same style as {@code HistoryQueryService}).
 */
@Service
public class DlqQueryService {

  private static final int MAX_PAGE_SIZE = 100;
  private static final Sort RECEIVED_DESC = Sort.by(Sort.Direction.DESC, "receivedAt");

  private final DeadLetterRecordRepository repository;

  public DlqQueryService(DeadLetterRecordRepository repository) {
    this.repository = repository;
  }

  /**
   * Lists dead letters with optional filters.
   *
   * @param domain optional domain filter
   * @param transactionId optional transaction id filter
   * @param transactionType optional transaction type filter
   * @param originalTopic optional original-topic filter
   * @param failureClassification optional classification filter
   * @param from optional inclusive lower bound on {@code received_at}
   * @param to optional inclusive upper bound on {@code received_at}
   * @param page zero-based page
   * @param size page size (clamped to 100)
   * @return a page of dead-letter summaries, newest first
   */
  @Transactional(readOnly = true)
  public PageResponse<DeadLetterRecordResponse> list(
      @Nullable String domain,
      @Nullable String transactionId,
      @Nullable String transactionType,
      @Nullable String originalTopic,
      @Nullable String failureClassification,
      @Nullable Instant from,
      @Nullable Instant to,
      int page,
      int size) {
    var pageable = PageRequest.of(Math.max(page, 0), clampSize(size), RECEIVED_DESC);
    // All present filters AND-compose; single time bounds are honoured independently.
    Specification<DeadLetterRecord> spec =
        (root, criteria, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (present(domain)) {
            predicates.add(cb.equal(root.get("domain"), domain));
          }
          if (present(transactionId)) {
            predicates.add(cb.equal(root.get("transactionId"), transactionId));
          }
          if (present(transactionType)) {
            predicates.add(cb.equal(root.get("transactionType"), transactionType));
          }
          if (present(originalTopic)) {
            predicates.add(cb.equal(root.get("originalTopic"), originalTopic));
          }
          if (present(failureClassification)) {
            predicates.add(cb.equal(root.get("failureClassification"), failureClassification));
          }
          if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("receivedAt"), from));
          }
          if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("receivedAt"), to));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };
    var result = repository.findAll(spec, pageable);
    var items = result.getContent().stream().map(DeadLetterRecordResponse::summary).toList();
    return new PageResponse<>(
        items, result.getNumber(), result.getSize(), result.getTotalElements());
  }

  /**
   * Fetches a single dead letter by id, including the original payload and raw DLT JSON.
   *
   * @param id the projection row id
   * @return the detail response
   * @throws NotFoundException if absent
   */
  @Transactional(readOnly = true)
  public DeadLetterRecordResponse getById(String id) {
    return repository
        .findById(id)
        .map(DeadLetterRecordResponse::detail)
        .orElseThrow(() -> new NotFoundException("Dead letter not found: " + id));
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static int clampSize(int size) {
    return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
  }
}
