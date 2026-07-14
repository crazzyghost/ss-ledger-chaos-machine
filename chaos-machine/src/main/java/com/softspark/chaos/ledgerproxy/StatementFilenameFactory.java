package com.softspark.chaos.ledgerproxy;

import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionExportDto;
import jakarta.annotation.Nullable;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Derives the {@code Content-Disposition} filename for a downloaded statement.
 *
 * <p>This is the <strong>response-header-injection surface</strong> of the download route. The
 * account code it builds on is operator-influenced ledger data, so every part of the name is
 * sanitized down to {@code [A-Za-z0-9._-]}: a CR/LF would let a caller forge response headers, a
 * quote would break out of the quoted filename, and a path separator would let the name walk a
 * directory on whatever machine saves it. Nothing that is not explicitly allowed survives.
 */
public final class StatementFilenameFactory {

  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final int MAX_LENGTH = 120;
  private static final String DEFAULT_EXTENSION = "dat";

  private StatementFilenameFactory() {}

  /**
   * Builds {@code statement-{accountCode|accountId}-{from:yyyyMMdd}-{to:yyyyMMdd}.{ext}}.
   *
   * @param export the completed export, supplying the resolved window and the format
   * @param accountCode the account's hierarchical code (e.g. {@code ASSET.PLATFORM.FLOAT}); when
   *     {@code null} or blank — the lookup failed, or the ledger has no code for it — the account id
   *     is used instead, because a download must never fail over a cosmetic name
   * @return a sanitized, length-capped filename safe to place in a quoted {@code Content-Disposition}
   */
  public static String filenameFor(
      LedgerTransactionExportDto export, @Nullable String accountCode) {
    var account =
        accountCode != null && !accountCode.isBlank()
            ? accountCode
            : String.valueOf(export.accountId());

    var name =
        "statement-"
            + sanitize(account)
            + "-"
            + export.rangeFrom().format(DAY)
            + "-"
            + export.rangeTo().format(DAY);

    return cap(name) + "." + extensionFor(export.format());
  }

  /**
   * Maps the ledger's format name onto a file extension. Formats travel as strings (ADR-033), so a
   * format this build has never heard of still yields a downloadable file rather than a failure.
   *
   * @param format the ledger's format name ({@code CSV}/{@code PDF})
   * @return the lowercased, sanitized extension
   */
  static String extensionFor(@Nullable String format) {
    if (format == null) {
      return DEFAULT_EXTENSION;
    }
    var extension = sanitize(format.toLowerCase(Locale.ROOT));
    return extension.isBlank() ? DEFAULT_EXTENSION : extension;
  }

  /** Replaces every disallowed character with {@code -}, then collapses and trims the runs. */
  private static String sanitize(String raw) {
    var replaced = raw.replaceAll("[^A-Za-z0-9._-]", "-");
    var collapsed = replaced.replaceAll("-{2,}", "-");
    return collapsed.replaceAll("^-+|-+$", "");
  }

  private static String cap(String name) {
    return name.length() <= MAX_LENGTH ? name : name.substring(0, MAX_LENGTH);
  }
}
