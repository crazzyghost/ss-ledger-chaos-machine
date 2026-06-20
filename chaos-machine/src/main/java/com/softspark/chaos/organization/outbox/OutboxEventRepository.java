package com.softspark.chaos.organization.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link OutboxEvent} rows.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

  /**
   * Returns outbox rows in the given status, oldest first, bounded by the supplied page.
   *
   * @param status   the status to filter on
   * @param pageable the page bound (used to cap the claim batch size)
   * @return matching rows ordered by creation time ascending
   */
  List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
