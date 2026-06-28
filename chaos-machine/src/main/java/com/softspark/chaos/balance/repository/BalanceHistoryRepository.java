package com.softspark.chaos.balance.repository;

import com.softspark.chaos.balance.model.BalanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository over the {@code balance_history} projection. {@link #existsByEventId(String)} backs the
 * idempotent upsert (Task 001); the nested + flat/batch queries compose account scope and time
 * filters via {@link JpaSpecificationExecutor} (Task 002).
 */
@Repository
public interface BalanceHistoryRepository
    extends JpaRepository<BalanceHistory, String>, JpaSpecificationExecutor<BalanceHistory> {

  boolean existsByEventId(String eventId);
}
