package com.softspark.chaos.history.repository;

import com.softspark.chaos.history.model.PublishRecord;
import com.softspark.chaos.run.dto.RunGroupRow;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

  /**
   * Projects the untracked publish records (those with no {@code batch_id}) into lightweight
   * {@link RunGroupRow}s, newest first, for in-memory grouping by {@code correlation_id} in the
   * run-grouped feed ({@code GET /api/v0/runs}).
   *
   * <p>Tracked-run events carry a {@code batch_id} (stamped via {@code recordBatch}) and so are
   * excluded here — they are represented authoritatively by their {@code batch_run} row, which avoids
   * double-counting. The {@code Pageable} bounds the scan window (the service logs if the cap is hit);
   * {@code created_at} is selected as an attribute (converter applied) rather than aggregated, so the
   * service can compute min/max on real instants.
   *
   * @param pageable the scan window (size = the configured run-feed scan limit, page 0)
   * @return the untracked rows, newest first, up to the scan window
   */
  @Query(
      "SELECT new com.softspark.chaos.run.dto.RunGroupRow("
          + "p.id, p.correlationId, p.eventType, p.status, p.intentionalFailure, p.createdAt) "
          + "FROM PublishRecord p WHERE p.batchId IS NULL ORDER BY p.createdAt DESC")
  List<RunGroupRow> findUntrackedRunRows(Pageable pageable);
}
