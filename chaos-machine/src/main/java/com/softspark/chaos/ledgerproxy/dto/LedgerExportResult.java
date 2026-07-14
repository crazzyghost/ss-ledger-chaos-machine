package com.softspark.chaos.ledgerproxy.dto;

/**
 * The outcome of a create-export call, carrying the ledger's own {@code 201} vs {@code 200}
 * distinction so the controller can echo it rather than flattening both to {@code 200} (ADR-035).
 *
 * <p>The ledger answers {@code 200} when the request resolved to a window + format for which an
 * export is <em>already active</em>; it returns that existing export instead of creating a second
 * one. The UI needs to tell the two apart — "export started" and "already running" are different
 * things to say to an operator.
 *
 * @param created {@code true} when the ledger created a new export ({@code 201}); {@code false} when
 *     the caller joined the active duplicate ({@code 200})
 * @param export the created or joined export
 */
public record LedgerExportResult(boolean created, LedgerTransactionExportDto export) {}
