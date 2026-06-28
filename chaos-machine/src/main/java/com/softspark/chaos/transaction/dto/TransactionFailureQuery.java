package com.softspark.chaos.transaction.dto;

import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Browse/single-filter parameters for the transaction-failures endpoint. The batch
 * ({@code transactionRequestIds}) path is handled separately in the controller/service.
 *
 * @param transactionRequestId single correlation-key lookup (the run-page poll)
 * @param transactionType optional filter by transaction type
 * @param failureCode optional filter by failure code
 * @param ledgerTransactionId optional filter by the ledger recording id
 * @param from optional start of the occurred-at range
 * @param to optional end of the occurred-at range
 * @param page zero-based page number (default 0)
 * @param size page size (default 20, max 100)
 */
public record TransactionFailureQuery(
    @Nullable String transactionRequestId,
    @Nullable String transactionType,
    @Nullable String failureCode,
    @Nullable String ledgerTransactionId,
    @Nullable Instant from,
    @Nullable Instant to,
    int page,
    int size) {

  public TransactionFailureQuery {
    if (page < 0) {
      page = 0;
    }
    size = Math.min(Math.max(size, 1), 100);
  }
}
