package com.softspark.chaos.ledgerproxy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO mirroring the ledger's unadjusted trial-balance response for a period.
 *
 * <p>Returned by {@code GET /api/v0/reporting/trial-balance} on the ledger and passed through
 * unchanged by the chaos read-proxy. The chaos machine computes nothing here — it is a transparent
 * gateway over the ledger's authoritative report (ADR-015).
 *
 * <p>{@code isBalanced} carries an explicit {@link JsonProperty} mapping: a boolean record component
 * named {@code isBalanced} is otherwise at the mercy of Jackson's {@code is}-prefix property
 * derivation, which could expose/consume it as {@code "balanced"}. Pinning the wire name to
 * {@code "isBalanced"} keeps deserialization from the ledger and serialization to the admin UI
 * symmetric and stable (covered by {@code TrialBalanceDtoTest}).
 *
 * @param from the inclusive start of the reported period (echoed from the request)
 * @param to the exclusive end of the reported period (echoed from the request)
 * @param currency the ISO-4217 currency the report was scoped to, or {@code null} for all currencies
 * @param totalDebits sum of all debit movement across the reported accounts
 * @param totalCredits sum of all credit movement across the reported accounts
 * @param isBalanced whether total debits equal total credits (a {@code false} result is valid during
 *     chaos and renders a warning, not an error)
 * @param numberOfAccounts the number of accounts with movement in the period
 * @param accounts the per-account breakdown rows
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrialBalanceDto(
    Instant from,
    Instant to,
    @Nullable String currency,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalDebits,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCredits,
    @JsonProperty("isBalanced") boolean isBalanced,
    int numberOfAccounts,
    List<TrialBalanceEntryDto> accounts) {}
