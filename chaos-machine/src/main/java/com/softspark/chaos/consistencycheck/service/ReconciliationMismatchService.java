package com.softspark.chaos.consistencycheck.service;

import com.softspark.chaos.consistencycheck.dto.ReconciliationMismatchDto;
import com.softspark.chaos.consistencycheck.dto.ReconciliationMismatchPollResponse;
import com.softspark.chaos.consistencycheck.repository.ReconciliationMismatchRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Service for polling reconciliation mismatches.
 *
 * <p>Supports the chaos UI's toast-notification use case: {@code GET
 * /reconciliation-mismatches?since=} returns all mismatches consumed after {@code since}, ordered
 * ascending by {@code consumedAt}.
 */
@Service
public class ReconciliationMismatchService {

  private final ReconciliationMismatchRepository repository;

  public ReconciliationMismatchService(ReconciliationMismatchRepository repository) {
    this.repository = repository;
  }

  /**
   * Polls for reconciliation mismatches consumed after a given timestamp.
   *
   * @param since the exclusive lower bound (consumed_at &gt; since)
   * @param size the page size
   * @return a page of mismatches with the nextSince cursor
   */
  public ReconciliationMismatchPollResponse pollMismatches(LocalDateTime since, int size) {
    var pageable = PageRequest.of(0, size, Sort.by("consumedAt").ascending());
    var page = repository.findAllByConsumedAtAfter(since, pageable);

    var items = page.getContent().stream().map(ReconciliationMismatchDto::from).toList();

    // nextSince is the consumedAt of the last row, or the original since if no rows
    var nextSince = items.isEmpty() ? since : items.getLast().consumedAt();

    return new ReconciliationMismatchPollResponse(
        items,
        items.size(),
        0, // always page 0 (polling, not traditional pagination)
        size,
        items.size() == size, // hasNext heuristic: if page is full, there might be more
        nextSince);
  }
}
