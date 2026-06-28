package com.softspark.chaos.reservation.repository;

import com.softspark.chaos.reservation.model.Reservation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository over the stateful {@code reservation} projection (keyed by {@code reservation_id}).
 * {@link #findByTransactionId(String)} backs the run-page toast watch (usually one row); the
 * remaining finders back the flat query, and the per-VA list composes filters via
 * {@link JpaSpecificationExecutor} (Task 002).
 */
@Repository
public interface ReservationRepository
    extends JpaRepository<Reservation, String>, JpaSpecificationExecutor<Reservation> {

  List<Reservation> findByTransactionId(String transactionId);

  Page<Reservation> findByDisbursementBatchId(String disbursementBatchId, Pageable pageable);

  Page<Reservation> findByAccountIdIn(Collection<String> accountIds, Pageable pageable);

  Page<Reservation> findByStatus(String status, Pageable pageable);
}
