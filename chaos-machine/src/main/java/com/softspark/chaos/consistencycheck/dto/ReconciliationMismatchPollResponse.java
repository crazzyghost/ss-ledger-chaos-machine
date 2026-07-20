package com.softspark.chaos.consistencycheck.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for polling reconciliation mismatches.
 *
 * <p>Returned by {@code GET /reconciliation-mismatches?since=}. The {@code nextSince} cursor is
 * the {@code consumedAt} of the last returned row, or the original {@code since} if no rows were
 * returned.
 */
public record ReconciliationMismatchPollResponse(
    List<ReconciliationMismatchDto> items,
    int totalElements,
    int page,
    int size,
    boolean hasNext,
    LocalDateTime nextSince) {}
