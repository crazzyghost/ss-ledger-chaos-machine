package com.softspark.chaos.balance.service;

import com.softspark.chaos.balance.dto.BalanceHistoryResponse;
import com.softspark.chaos.balance.model.BalanceHistory;
import com.softspark.chaos.balance.repository.BalanceHistoryRepository;
import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.BadRequestException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service over {@code balance_history} (ADR-027). Newest-first by {@code (occurred_at DESC,
 * last_entry_sequence DESC)} — robust even when {@code last_entry_sequence} is {@code 0}.
 */
@Service
public class BalanceHistoryQueryService {

  /** Upper bound on the flat/batch {@code accountId} cardinality. */
  static final int MAX_ACCOUNT_BATCH = 50;

  private static final int MAX_PAGE_SIZE = 100;
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("lastEntrySequence"));

  private final BalanceHistoryRepository repository;

  public BalanceHistoryQueryService(BalanceHistoryRepository repository) {
    this.repository = repository;
  }

  /**
   * Balance history for a single account (the Balance tab), optionally time-filtered.
   *
   * @param accountId the VA id
   * @param from optional inclusive lower bound on {@code occurred_at}
   * @param to optional inclusive upper bound on {@code occurred_at}
   * @param page zero-based page
   * @param size page size (clamped to 100)
   * @return a page of balance-history rows, newest first
   */
  @Transactional(readOnly = true)
  public PageResponse<BalanceHistoryResponse> forAccount(
      String accountId, @Nullable Instant from, @Nullable Instant to, int page, int size) {
    var pageable = PageRequest.of(Math.max(page, 0), clampSize(size), NEWEST_FIRST);
    Specification<BalanceHistory> spec =
        (root, criteria, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(cb.equal(root.get("accountId"), accountId));
          if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
          }
          if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("occurredAt"), to));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };
    return toPage(repository.findAll(spec, pageable));
  }

  /**
   * Balance history across several accounts in one call (the run-page balance watch).
   *
   * @param rawAccountIds the account ids (may contain blanks/duplicates)
   * @param from optional inclusive lower bound on {@code occurred_at}
   * @param to optional inclusive upper bound on {@code occurred_at}
   * @param page zero-based page
   * @param size page size (clamped to 100)
   * @return a page of balance-history rows across the accounts, newest first
   * @throws BadRequestException if the id set is empty or exceeds {@link #MAX_ACCOUNT_BATCH}
   */
  @Transactional(readOnly = true)
  public PageResponse<BalanceHistoryResponse> forAccounts(
      List<String> rawAccountIds, @Nullable Instant from, @Nullable Instant to, int page, int size) {
    List<String> ids = sanitize(rawAccountIds);
    if (ids.isEmpty()) {
      throw new BadRequestException("accountId must contain at least one id");
    }
    if (ids.size() > MAX_ACCOUNT_BATCH) {
      throw new BadRequestException("accountId exceeds the maximum of " + MAX_ACCOUNT_BATCH);
    }
    var pageable = PageRequest.of(Math.max(page, 0), clampSize(size), NEWEST_FIRST);
    Specification<BalanceHistory> spec =
        (root, criteria, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(root.get("accountId").in(ids));
          if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
          }
          if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("occurredAt"), to));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };
    return toPage(repository.findAll(spec, pageable));
  }

  private static List<String> sanitize(List<String> raw) {
    return raw == null
        ? List.of()
        : raw.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();
  }

  private static int clampSize(int size) {
    return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
  }

  private static PageResponse<BalanceHistoryResponse> toPage(Page<BalanceHistory> page) {
    var items = page.getContent().stream().map(BalanceHistoryResponse::from).toList();
    return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
  }
}
