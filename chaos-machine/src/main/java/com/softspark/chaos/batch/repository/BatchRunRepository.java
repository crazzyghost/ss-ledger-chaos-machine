package com.softspark.chaos.batch.repository;

import com.softspark.chaos.batch.model.BatchRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link BatchRun} entities.
 */
@Repository
public interface BatchRunRepository extends JpaRepository<BatchRun, String> {

  /**
   * Returns all batch runs ordered by creation time descending.
   *
   * @param pageable pagination parameters
   * @return a page of batch runs
   */
  Page<BatchRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
