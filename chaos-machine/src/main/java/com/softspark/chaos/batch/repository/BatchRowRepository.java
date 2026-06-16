package com.softspark.chaos.batch.repository;

import com.softspark.chaos.batch.enumeration.BatchRowStatus;
import com.softspark.chaos.batch.model.BatchRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link BatchRow} entities.
 */
@Repository
public interface BatchRowRepository extends JpaRepository<BatchRow, String> {

  /**
   * Returns all rows for a batch run, ordered by row number.
   *
   * @param batchId the batch run id
   * @param pageable pagination parameters
   * @return a page of batch rows
   */
  Page<BatchRow> findByBatchIdOrderByRowNumber(String batchId, Pageable pageable);

  /**
   * Counts rows in a given batch with the specified status.
   *
   * @param batchId the batch run id
   * @param status the row status to count
   * @return the count
   */
  long countByBatchIdAndStatus(String batchId, BatchRowStatus status);
}
