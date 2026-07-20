package com.softspark.chaos.transaction.repository;

import com.softspark.chaos.transaction.model.TransactionFailure;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository over the {@code transaction_failure} projection.
 *
 * <p>{@link #existsByEventId(String)} backs the idempotent upsert (Task 002); the batch lookup backs
 * the "Sent" tab. Composable browse filters use {@link JpaSpecificationExecutor}. All filters hit
 * indexed columns.
 */
@Repository
public interface TransactionFailureRepository
    extends JpaRepository<TransactionFailure, String>,
        JpaSpecificationExecutor<TransactionFailure> {

  boolean existsByEventId(String eventId);

  List<TransactionFailure> findByTransactionRequestIdIn(Collection<String> transactionRequestIds);
}
