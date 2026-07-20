package com.softspark.chaos.dlq.repository;

import com.softspark.chaos.dlq.model.DeadLetterRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository over the {@code dlq} projection. {@code
 * existsByDltTopicAndDltPartitionAndDltOffset} backs the idempotent upsert (Task 001); composable
 * list filters use {@link JpaSpecificationExecutor} (Task 002).
 */
@Repository
public interface DeadLetterRecordRepository
    extends JpaRepository<DeadLetterRecord, String>, JpaSpecificationExecutor<DeadLetterRecord> {

  boolean existsByDltTopicAndDltPartitionAndDltOffset(
      String dltTopic, int dltPartition, long dltOffset);
}
