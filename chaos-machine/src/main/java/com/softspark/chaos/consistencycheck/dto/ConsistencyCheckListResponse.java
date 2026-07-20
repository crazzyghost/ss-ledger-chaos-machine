package com.softspark.chaos.consistencycheck.dto;

import com.softspark.chaos.ledgerproxy.dto.LedgerPageDto;
import java.util.List;

/**
 * Chaos-facing DTO for a paginated list of consistency checks.
 *
 * <p>Maps from {@code LedgerPageDto<LedgerConsistencyCheckDto>}.
 */
public record ConsistencyCheckListResponse(
    List<ConsistencyCheckResponse> items, int totalElements, int page, int size, boolean hasNext) {

  public static ConsistencyCheckListResponse fromPage(
      LedgerPageDto<com.softspark.chaos.ledgerproxy.dto.LedgerConsistencyCheckDto> page) {
    var items = page.data().stream().map(ConsistencyCheckResponse::from).toList();
    return new ConsistencyCheckListResponse(
        items, (int) page.total(), page.page(), page.pageSize(), page.page() < page.pages() - 1);
  }
}
