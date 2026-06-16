package com.softspark.chaos.history.repository;

import com.softspark.chaos.history.model.PublishRecord;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link PublishRecord} entities.
 */
@Repository
public interface PublishRecordRepository extends JpaRepository<PublishRecord, String> {

  /**
   * Finds publish records by event type.
   *
   * @param eventType the event type string
   * @param pageable pagination parameters
   * @return a page of matching records
   */
  Page<PublishRecord> findByEventType(String eventType, Pageable pageable);

  /**
   * Finds publish records where the source or destination VA id matches.
   *
   * @param sourceVaId the source VA id
   * @param destinationVaId the destination VA id
   * @param pageable pagination parameters
   * @return a page of matching records
   */
  Page<PublishRecord> findBySourceVaIdOrDestinationVaId(
      String sourceVaId, String destinationVaId, Pageable pageable);

  /**
   * Finds publish records by correlation id.
   *
   * @param correlationId the correlation id
   * @param pageable pagination parameters
   * @return a page of matching records
   */
  Page<PublishRecord> findByCorrelationId(String correlationId, Pageable pageable);

  /**
   * Finds publish records belonging to a batch run.
   *
   * @param batchId the batch run id
   * @param pageable pagination parameters
   * @return a page of matching records
   */
  Page<PublishRecord> findByBatchId(String batchId, Pageable pageable);

  /**
   * Finds publish records created within the given time range.
   *
   * @param from the start of the range (inclusive)
   * @param to the end of the range (inclusive)
   * @param pageable pagination parameters
   * @return a page of matching records
   */
  Page<PublishRecord> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

  /**
   * Finds publish records by status.
   *
   * @param status the status string (e.g., {@code "PUBLISHED"} or {@code "FAILED"})
   * @param pageable pagination parameters
   * @return a page of matching records
   */
  Page<PublishRecord> findByStatus(String status, Pageable pageable);
}
