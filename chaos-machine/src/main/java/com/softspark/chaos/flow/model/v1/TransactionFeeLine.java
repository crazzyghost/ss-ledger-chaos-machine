package com.softspark.chaos.flow.model.v1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.math.BigDecimal;

/**
 * A single fee line on a fee-bearing event payload, shared by {@code collection.completed} and
 * {@code disbursement.completed}.
 *
 * <p>Matches the authoritative ledger {@code TransactionFeeLine} record: {@code fee_type} is the
 * ledger {@code TransactionFeeType} name ({@code PLATFORM}, {@code PROVIDER}, {@code FX_MARGIN},
 * {@code SURCHARGE} — note: <em>no</em> {@code _FEE} suffix), {@code amount} is the fee amount,
 * {@code fee_code} is a required reference the ledger does not default, and {@code destination_va_id}
 * is the system fee-revenue VA credited with this fee.
 *
 * @param feeType the ledger {@code TransactionFeeType} name (e.g. {@code PLATFORM})
 * @param amount the fee amount
 * @param feeCode the fee reference code (ledger-required; never blank on the wire)
 * @param destinationVaId the system fee-revenue virtual account credited with this fee
 */
@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TransactionFeeLine(
    String feeType, BigDecimal amount, String feeCode, String destinationVaId) {}
