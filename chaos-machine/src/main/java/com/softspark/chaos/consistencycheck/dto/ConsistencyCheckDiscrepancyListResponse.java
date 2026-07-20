package com.softspark.chaos.consistencycheck.dto;

import com.softspark.chaos.ledgerproxy.dto.LedgerPageDto;
import java.util.List;

/**
 * Chaos-facing DTO for a paginated list of consistency check discrepancies.
 *
 * <p>Maps from {@code LedgerPageDto<LedgerConsistencyCheckDiscrepancyDto>}.
 */
public record ConsistencyCheckDiscrepancyListResponse(
    List<ConsistencyCheckDiscrepancyResponse> items,
    int totalElements,
    int page,
    int size,
    boolean hasNext) {

  public static ConsistencyCheckDiscrepancyListResponse fromPage(
      LedgerPageDto<com.softspark.chaos.ledgerproxy.dto.LedgerConsistencyCheckDiscrepancyDto>
          page) {
    var items = page.data().stream().map(ConsistencyCheckDiscrepancyResponse::from).toList();
    return new ConsistencyCheckDiscrepancyListResponse(
        items, (int) page.total(), page.page(), page.pageSize(), page.page() < page.pages() - 1);
  }
}
