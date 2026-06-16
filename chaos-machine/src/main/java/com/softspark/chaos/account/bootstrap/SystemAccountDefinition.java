package com.softspark.chaos.account.bootstrap;

import com.softspark.chaos.account.bootstrap.validator.HierarchicalCode;
import com.softspark.chaos.account.enumeration.AccountCategory;
import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.base.validation.ISO4217;
import com.softspark.chaos.base.validation.IsInEnum;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.springframework.validation.annotation.Validated;

/**
 * Describes a single system account to be provisioned in the ledger.
 *
 * <p>Instances are loaded from {@code chaos-bootstrap.yml} via {@link BootstrapProperties} and
 * validated programmatically by {@link SystemAccountCatalogValidator} before provisioning.
 *
 * @param role            the application-level role this account fulfils
 * @param accountCode     dot-separated hierarchical code, e.g. {@code ASSET.PLATFORM.FLOAT}
 * @param accountName     human-readable display name
 * @param accountCategory one of the values in {@link AccountCategory}
 * @param currency        ISO-4217 currency code
 * @param ownershipType   one of the values in {@link AccountOwnershipType}
 * @param parentRole      optional role of the parent account (defines ledger hierarchy)
 * @param overdraftLimit  optional non-negative overdraft limit
 * @param minimumBalance  optional non-negative minimum balance floor
 */
@RecordBuilder
@Validated
public record SystemAccountDefinition(
    @NotNull AccountRole role,
    @NotBlank @HierarchicalCode String accountCode,
    @NotBlank String accountName,
    @NotBlank @IsInEnum(enumClass = AccountCategory.class) String accountCategory,
    @NotBlank @ISO4217 String currency,
    @NotBlank @IsInEnum(enumClass = AccountOwnershipType.class) String ownershipType,
    @Nullable AccountRole parentRole,
    @Nullable @PositiveOrZero BigDecimal overdraftLimit,
    @Nullable @PositiveOrZero BigDecimal minimumBalance) {}
