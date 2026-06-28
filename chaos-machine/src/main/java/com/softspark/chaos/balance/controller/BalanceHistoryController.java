package com.softspark.chaos.balance.controller;

import com.softspark.chaos.balance.dto.BalanceHistoryResponse;
import com.softspark.chaos.balance.service.BalanceHistoryQueryService;
import com.softspark.chaos.base.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API over the {@code balance_history} projection (ADR-027): a per-VA history (the Balance tab)
 * and a flat/batch history across several accounts (the run-page balance watch). Both are
 * newest-first and AUTH-gated like every {@code /api/v0/**} route.
 */
@RestController
@Tag(name = "Balance History", description = "Per-account ledger balance-update history")
public class BalanceHistoryController {

  private final BalanceHistoryQueryService queryService;

  public BalanceHistoryController(BalanceHistoryQueryService queryService) {
    this.queryService = queryService;
  }

  /**
   * Balance-update history for a single virtual account.
   *
   * @param vaId the virtual account id
   * @param from optional inclusive lower bound on {@code occurred_at} (ISO-8601)
   * @param to optional inclusive upper bound on {@code occurred_at} (ISO-8601)
   * @param page zero-based page (default 0)
   * @param size page size (default 20, max 100)
   * @return a page of balance-history rows, newest first (empty if no history)
   */
  @GetMapping("/api/v0/virtual-accounts/{vaId}/balance-history")
  @Operation(summary = "Balance history for a virtual account")
  public ResponseEntity<PageResponse<BalanceHistoryResponse>> forVirtualAccount(
      @PathVariable String vaId,
      @RequestParam(required = false) @Nullable Instant from,
      @RequestParam(required = false) @Nullable Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(queryService.forAccount(vaId, from, to, page, size));
  }

  /**
   * Flat/batch balance-update history across one or several accounts.
   *
   * @param accountId repeatable account id parameter (1..50)
   * @param from optional inclusive lower bound on {@code occurred_at} (ISO-8601)
   * @param to optional inclusive upper bound on {@code occurred_at} (ISO-8601)
   * @param page zero-based page (default 0)
   * @param size page size (default 20, max 100)
   * @return a page of balance-history rows across the accounts, newest first
   */
  @GetMapping("/api/v0/balance-history")
  @Operation(summary = "Flat/batch balance history across accounts")
  public ResponseEntity<PageResponse<BalanceHistoryResponse>> flat(
      @RequestParam List<String> accountId,
      @RequestParam(required = false) @Nullable Instant from,
      @RequestParam(required = false) @Nullable Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(queryService.forAccounts(accountId, from, to, page, size));
  }
}
