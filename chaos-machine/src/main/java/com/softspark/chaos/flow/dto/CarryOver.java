package com.softspark.chaos.flow.dto;

/**
 * A single initiated-to-secondary field copy within a {@link FlowLifecycle}.
 *
 * <p>When a lifecycle advances from {@code initiated} to {@code completed}/{@code failed}, the value
 * of {@code fromField} on the initiated form is copied into {@code toField} on the secondary form.
 * The copy is applied only when the secondary form actually declares a descriptor named
 * {@code toField} — so a single carry-over list can serve both secondary phases even when a source
 * field maps to a different target in each (e.g. {@code virtual_account_id} → {@code source_va_id}
 * for completed but {@code virtual_account_id} → {@code virtual_account_id} for failed).
 *
 * @param fromField the initiated-form field name to copy from
 * @param toField the secondary-form field name to copy into
 */
public record CarryOver(String fromField, String toField) {}
