package com.softspark.chaos.account.dto;

import com.softspark.chaos.account.enumeration.AccountCategory;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.enumeration.Channel;
import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Response record representing a chart of accounts role.
 *
 * @param role         the account role
 * @param accountCode  the account code
 * @param category     the account category
 * @param currency     the currency code (ISO-4217)
 * @param channel      optional channel
 * @param defaultVaId  the default virtual account ID for this role
 */
@RecordBuilder
public record ChartOfAccountsRoleResponse(
    AccountRole role,
    String accountCode,
    AccountCategory category,
    String currency,
    Channel channel,
    String defaultVaId) {}
