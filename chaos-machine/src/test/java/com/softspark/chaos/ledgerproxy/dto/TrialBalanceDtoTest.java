package com.softspark.chaos.ledgerproxy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Deserialization contract tests for {@link TrialBalanceDto} / {@link TrialBalanceEntryDto} against
 * a captured {@code ss-ledger-service} trial-balance sample.
 *
 * <p>Pins the field set and, critically, the {@code "isBalanced"} JSON mapping (the Jackson
 * {@code is}-prefix pitfall) so a ledger contract change or a Jackson naming regression fails loudly
 * here rather than silently null-ing a field downstream.
 */
@DisplayName("TrialBalanceDto")
class TrialBalanceDtoTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
  }

  @Test
  @DisplayName("deserializes a balanced, all-currencies report (null currency + SYSTEM null owner)")
  void deserializesBalancedReport() throws Exception {
    var json =
        """
        {
          "from": "2026-06-01T00:00:00Z",
          "to": "2026-07-01T00:00:00Z",
          "currency": null,
          "totalDebits": "1500.75",
          "totalCredits": "1500.75",
          "isBalanced": true,
          "numberOfAccounts": 2,
          "accounts": [
            {
              "accountId": "acct-sys-1",
              "accountCode": "ASSET.PLATFORM.FLOAT",
              "accountName": "Platform Float",
              "accountOwnerId": null,
              "accountOwnershipType": "SYSTEM",
              "currency": "GHS",
              "totalDebits": "1500.75",
              "totalCredits": "0.00",
              "netMovement": "1500.75"
            },
            {
              "accountId": "acct-org-1",
              "accountCode": "LIABILITY.ORG.WALLET",
              "accountName": "Org Wallet",
              "accountOwnerId": "org-42",
              "accountOwnershipType": "ORGANIZATION",
              "currency": "GHS",
              "totalDebits": "0.00",
              "totalCredits": "1500.75",
              "netMovement": "-1500.75"
            }
          ]
        }
        """;

    var dto = mapper.readValue(json, TrialBalanceDto.class);

    assertThat(dto.from()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
    assertThat(dto.to()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
    assertThat(dto.currency()).isNull();
    assertThat(dto.totalDebits()).isEqualByComparingTo(new BigDecimal("1500.75"));
    assertThat(dto.totalCredits()).isEqualByComparingTo(new BigDecimal("1500.75"));
    assertThat(dto.isBalanced()).isTrue();
    assertThat(dto.numberOfAccounts()).isEqualTo(2);
    assertThat(dto.accounts()).hasSize(2);

    var systemRow = dto.accounts().get(0);
    assertThat(systemRow.accountId()).isEqualTo("acct-sys-1");
    assertThat(systemRow.accountCode()).isEqualTo("ASSET.PLATFORM.FLOAT");
    assertThat(systemRow.accountOwnershipType()).isEqualTo("SYSTEM");
    assertThat(systemRow.accountOwnerId()).isNull();
    assertThat(systemRow.currency()).isEqualTo("GHS");
    assertThat(systemRow.netMovement()).isEqualByComparingTo(new BigDecimal("1500.75"));

    var orgRow = dto.accounts().get(1);
    assertThat(orgRow.accountOwnerId()).isEqualTo("org-42");
    assertThat(orgRow.accountOwnershipType()).isEqualTo("ORGANIZATION");
    assertThat(orgRow.netMovement()).isEqualByComparingTo(new BigDecimal("-1500.75"));
  }

  @Test
  @DisplayName("deserializes isBalanced=false (a valid out-of-balance result)")
  void deserializesOutOfBalanceReport() throws Exception {
    var json =
        """
        {
          "from": "2026-06-01T00:00:00Z",
          "to": "2026-07-01T00:00:00Z",
          "currency": "GHS",
          "totalDebits": "100.00",
          "totalCredits": "90.00",
          "isBalanced": false,
          "numberOfAccounts": 0,
          "accounts": []
        }
        """;

    var dto = mapper.readValue(json, TrialBalanceDto.class);

    assertThat(dto.isBalanced()).isFalse();
    assertThat(dto.currency()).isEqualTo("GHS");
    assertThat(dto.accounts()).isEmpty();
  }

  @Test
  @DisplayName(
      "serializes the isBalanced component back to the \"isBalanced\" key (not \"balanced\")")
  void serializesIsBalancedKey() throws Exception {
    var dto =
        new TrialBalanceDto(
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-07-01T00:00:00Z"),
            null,
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            true,
            0,
            java.util.List.of());

    var json = mapper.writeValueAsString(dto);

    assertThat(json).contains("\"isBalanced\":true");
    assertThat(json).doesNotContain("\"balanced\"");
  }
}
