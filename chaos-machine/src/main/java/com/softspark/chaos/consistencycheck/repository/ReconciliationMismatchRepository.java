package com.softspark.chaos.consistencycheck.repository;

import com.softspark.chaos.consistencycheck.model.ReconciliationMismatch;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link ReconciliationMismatch}.
 *
 * <p>Supports polling queries for the chaos UI (GET /reconciliation-mismatches?since=).
 */
@Repository
public interface ReconciliationMismatchRepository
    extends JpaRepository<ReconciliationMismatch, String> {

  /**
   * Finds all mismatches consumed after a given timestamp, ordered ascending.
   *
   * @param since the exclusive lower bound
   * @param pageable the page request (must include {@code Sort.by("consumedAt").ascending()})
   * @return a page of mismatches
   */
  Page<ReconciliationMismatch> findAllByConsumedAtAfter(LocalDateTime since, Pageable pageable);
}
