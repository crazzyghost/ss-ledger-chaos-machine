package com.softspark.chaos.ledgerproxy;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.BadGatewayException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.InternalServerErrorException;
import com.softspark.chaos.ledgerproxy.circuitbreaker.CircuitBreakerOpenException;
import com.softspark.chaos.ledgerproxy.dto.LedgerPageDto;
import com.softspark.chaos.ledgerproxy.dto.LedgerTransactionExportDto;
import com.softspark.chaos.ledgerproxy.dto.TransactionExportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Proxy controller for the ledger's account-statement exports — the chaos proxy's first
 * <em>commands</em> ({@code PUT}/{@code DELETE}), which is why they live here rather than on the
 * honestly-named read-only {@link LedgerReadController} (ADR-033).
 *
 * <p>This controller re-validates nothing and re-implements nothing. Formats, range types, the
 * half-open {@code [from, to)} UTC window, the 366-day cap, and the active-window idempotency rule
 * that makes a duplicate request a {@code 200} rather than a second export are all the ledger's
 * contract; parameters are forwarded, and failures — including the ledger's own error message —
 * are surfaced with their real status (ADR-035).
 *
 * <p>No response from this controller carries a presigned download URL. {@link
 * TransactionExportResponse} has no component for one (ADR-034). The artifact is reached instead
 * through {@link #downloadExport}, the one route here with no ledger counterpart: the chaos machine
 * fetches the object itself and streams the bytes, so the presigned capability never leaves the
 * backend.
 */
@RestController
@RequestMapping("/api/v0/ledger/accounts/{accountId}/transaction-exports")
@Tag(
    name = "Ledger Statement Exports",
    description = "Proxied account-statement exports (create, poll, list, cancel, download)")
@SecurityRequirement(name = "bearerAuth")
public class LedgerExportController {

  private static final String COMPLETED = "COMPLETED";

  private final LedgerClient ledgerClient;
  private final ArtifactFetcher artifactFetcher;

  /**
   * Constructs the controller.
   *
   * @param ledgerClient the ledger client
   * @param artifactFetcher the server-side fetcher for statement artifacts
   */
  public LedgerExportController(LedgerClient ledgerClient, ArtifactFetcher artifactFetcher) {
    this.ledgerClient = ledgerClient;
    this.artifactFetcher = artifactFetcher;
  }

  /**
   * Requests a statement export, or joins the one already active for the same window and format.
   *
   * <p>Mirrors the ledger's contract exactly, including its unusual shape: a {@code PUT} whose
   * every parameter is a query param and which has no request body. The {@code 201} (created) vs
   * {@code 200} (joined the active duplicate) distinction is the ledger's, preserved rather than
   * flattened — "export started" and "already running" are different things to tell an operator.
   *
   * @param accountId the ledger account to export
   * @param format the artifact format ({@code CSV}/{@code PDF}; case-insensitive)
   * @param rangeType the window kind ({@code DAILY}/{@code WEEKLY}/{@code MONTHLY}/{@code
   *     YEARLY}/{@code CUSTOM}; case-insensitive)
   * @param from the start of the window (ISO-8601 instant)
   * @param to the exclusive end of the window; the ledger requires it only for {@code CUSTOM}
   * @param request the HTTP request (for token extraction)
   * @return {@code 201} with the new export, or {@code 200} with the export it joined
   */
  @PutMapping
  @Operation(
      summary = "Create statement export",
      description =
          "Creates an export job, or returns the export already active for the same resolved window"
              + " and format (200). Poll the export at an interval of 2 seconds or more.")
  public ResponseEntity<TransactionExportResponse> createExport(
      @PathVariable String accountId,
      @RequestParam String format,
      @RequestParam String rangeType,
      @RequestParam Instant from,
      @RequestParam(required = false) Instant to,
      HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var result = ledgerClient.createExport(token, accountId, format, rangeType, from, to);
      return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
          .body(TransactionExportResponse.from(result.export()));
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Polls a statement export's status.
   *
   * <p>{@code downloadable} is {@code true} only once the export is {@code COMPLETED}; the presigned
   * URL behind it never crosses this boundary (ADR-034).
   *
   * @param accountId the ledger account the export belongs to
   * @param exportId the export id
   * @param request the HTTP request
   * @return the export
   */
  @GetMapping("/{exportId}")
  @Operation(
      summary = "Get statement export",
      description = "Polls an export's status; no download URL is ever returned")
  public ResponseEntity<TransactionExportResponse> getExport(
      @PathVariable String accountId, @PathVariable String exportId, HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var export = ledgerClient.getExport(token, accountId, exportId);
      return ResponseEntity.ok(TransactionExportResponse.from(export));
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Lists an account's statement exports, newest first.
   *
   * <p>{@code pageSize} keeps the ledger's name (rather than the chaos {@code perPage}) so the two
   * paths correspond 1:1, and is forwarded unclamped — the ledger caps it at 100 and rejects more
   * with a {@code 400} that now reaches the operator intact.
   *
   * @param accountId the ledger account whose exports are listed
   * @param status optional lifecycle-status filter
   * @param format optional format filter
   * @param page zero-based page number (default 0)
   * @param pageSize page size (default 20; the ledger caps it at 100)
   * @param request the HTTP request
   * @return a page of exports, newest first
   */
  @GetMapping
  @Operation(
      summary = "List statement exports",
      description = "Proxy to the ledger's export list, newest first")
  public ResponseEntity<PageResponse<TransactionExportResponse>> listExports(
      @PathVariable String accountId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String format,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var result = ledgerClient.listExports(token, accountId, status, format, page, pageSize);
      return ResponseEntity.ok(toPageResponse(result));
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Cancels a pending or in-progress statement export.
   *
   * <p>An export that has already finished is a {@code 409}, not a {@code 404} — it is right there
   * on the operator's screen (ADR-035).
   *
   * @param accountId the ledger account the export belongs to
   * @param exportId the export id
   * @param request the HTTP request
   * @return the export in its {@code CANCELLED} state
   */
  @DeleteMapping("/{exportId}")
  @Operation(
      summary = "Cancel statement export",
      description = "Cancels a PENDING or IN_PROGRESS export; a terminal export returns 409")
  public ResponseEntity<TransactionExportResponse> cancelExport(
      @PathVariable String accountId, @PathVariable String exportId, HttpServletRequest request) {
    var token = extractToken(request);
    try {
      var export = ledgerClient.cancelExport(token, accountId, exportId);
      return ResponseEntity.ok(TransactionExportResponse.from(export));
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }
  }

  /**
   * Streams a completed statement's artifact to the browser — the chaos machine's own route, with no
   * counterpart on the ledger.
   *
   * <p>The chaos machine resolves the export through the ledger (which mints a <strong>fresh</strong>
   * presigned URL on every read — so this route resolves on every call and caches nothing), fetches
   * the object from the store itself, and streams the bytes back. The presigned URL exists only
   * inside this method and {@link ArtifactFetcher}: it is never serialized, never logged, and never
   * reaches the browser (ADR-034). What the browser gets instead is an explicit
   * {@code Content-Disposition: attachment} with a real filename, so a PDF <em>saves</em> rather than
   * rendering inline in a tab and a CSV is not named after an S3 object key — neither of which the
   * presigned URL can do on its own, since S3 stores no {@code Content-Disposition} and the
   * {@code download} attribute on an anchor is ignored cross-origin.
   *
   * <p>An export that is not {@code COMPLETED} has no artifact, so it is a {@code 409} naming the
   * actual status — not a {@code 404} about a row the operator is looking at.
   *
   * @param accountId the ledger account the export belongs to
   * @param exportId the export id
   * @param request the HTTP request
   * @return {@code 200} streaming the artifact, with the format's content type and an attachment
   *     disposition
   */
  @GetMapping("/{exportId}/download")
  @Operation(
      summary = "Download a completed statement",
      description =
          "Streams the artifact through the gateway with Content-Disposition: attachment. The"
              + " ledger's presigned S3 URL never reaches the browser. A non-COMPLETED export is a"
              + " 409; an artifact-store failure is a 502.")
  public ResponseEntity<StreamingResponseBody> downloadExport(
      @PathVariable String accountId, @PathVariable String exportId, HttpServletRequest request) {
    var token = extractToken(request);

    LedgerTransactionExportDto export;
    try {
      export = ledgerClient.getExport(token, accountId, exportId);
    } catch (CircuitBreakerOpenException e) {
      throw new InternalServerErrorException("Ledger service temporarily unavailable");
    }

    if (!COMPLETED.equals(export.status())) {
      throw new ConflictException(
          "Export " + exportId + " is " + export.status() + " — there is no artifact to download");
    }
    if (export.downloadUrl() == null) {
      throw new BadGatewayException(
          "The ledger reported export " + exportId + " as COMPLETED but returned no download URL");
    }

    var filename = StatementFilenameFactory.filenameFor(export, accountCodeOf(token, accountId));
    var artifact = artifactFetcher.fetch(export.downloadUrl(), exportId);

    var response =
        ResponseEntity.ok()
            .contentType(contentTypeFor(export.format()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString());
    if (artifact.contentLength() != null) {
      response = response.contentLength(artifact.contentLength());
    }
    return response.body(artifact::writeTo);
  }

  /**
   * Best-effort lookup of the account's code, purely to give the downloaded file a meaningful name.
   *
   * <p>A slow, forbidden, or missing account must <strong>never</strong> fail a download the ledger
   * already said is ready — the filename falls back to the account id instead. That is the whole
   * reason this swallows everything.
   */
  @Nullable
  private String accountCodeOf(String token, String accountId) {
    try {
      return ledgerClient.getAccount(token, accountId).accountCode();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static MediaType contentTypeFor(String format) {
    return switch (format == null ? "" : format.toUpperCase(Locale.ROOT)) {
      case "CSV" -> MediaType.parseMediaType("text/csv");
      case "PDF" -> MediaType.APPLICATION_PDF;
      default -> MediaType.APPLICATION_OCTET_STREAM;
    };
  }

  private String extractToken(HttpServletRequest request) {
    var header = request.getHeader("Authorization");
    return header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
  }

  private PageResponse<TransactionExportResponse> toPageResponse(
      LedgerPageDto<LedgerTransactionExportDto> page) {
    var items =
        page.data() == null
            ? List.<TransactionExportResponse>of()
            : page.data().stream().map(TransactionExportResponse::from).toList();
    return new PageResponse<>(items, page.page(), page.pageSize(), page.total());
  }
}
