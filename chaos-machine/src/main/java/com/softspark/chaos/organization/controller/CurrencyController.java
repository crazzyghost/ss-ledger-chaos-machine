package com.softspark.chaos.organization.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.organization.dto.CreateCurrencyRequest;
import com.softspark.chaos.organization.dto.CurrencyResponse;
import com.softspark.chaos.organization.dto.UpdateCurrencyRequest;
import com.softspark.chaos.organization.service.CurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for currency reference-data operations.
 *
 * <p>Provides endpoints for creating, listing, retrieving, and updating currencies.
 */
@RestController
@RequestMapping("/api/v0/currencies")
@Tag(name = "Currencies", description = "Currency reference data")
public class CurrencyController {

  private final CurrencyService currencyService;

  public CurrencyController(CurrencyService currencyService) {
    this.currencyService = currencyService;
  }

  /**
   * Creates a new currency.
   *
   * @param request the creation request
   * @return the created currency
   */
  @PostMapping
  @Operation(
      summary = "Create a currency",
      description = "Creates a new currency with a server-generated UUID identifier")
  public ResponseEntity<CurrencyResponse> createCurrency(
      @Valid @RequestBody CreateCurrencyRequest request) {
    var created = currencyService.createCurrency(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Retrieves a currency by ID.
   *
   * @param currencyId the currency ID
   * @return the currency
   */
  @GetMapping("/{currencyId}")
  @Operation(summary = "Get a currency", description = "Retrieves a currency by its ID")
  public ResponseEntity<CurrencyResponse> getCurrency(@PathVariable String currencyId) {
    var currency = currencyService.getCurrency(currencyId);
    return ResponseEntity.ok(currency);
  }

  /**
   * Lists currencies with pagination.
   *
   * @param page    the page number (0-indexed)
   * @param perPage the number of items per page
   * @return a paginated list of currencies
   */
  @GetMapping
  @Operation(summary = "List currencies", description = "Lists currencies with pagination")
  public ResponseEntity<PageResponse<CurrencyResponse>> listCurrencies(
      @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false)
          Integer page,
      @Parameter(description = "Number of items per page (max 100)") @RequestParam(required = false)
          Integer perPage,
      @Parameter(description = "Search term matched against code or name")
          @RequestParam(required = false)
          String search,
      @Parameter(description = "Sort field (code, name, status, createdAt, updatedAt)")
          @RequestParam(required = false)
          String sortBy,
      @Parameter(description = "Sort direction (asc or desc)") @RequestParam(required = false)
          String sortDir) {
    var result = currencyService.listCurrencies(page, perPage, search, sortBy, sortDir);
    return ResponseEntity.ok(result);
  }

  /**
   * Updates an existing currency.
   *
   * @param currencyId the currency ID
   * @param request    the update request
   * @return the updated currency
   */
  @PutMapping("/{currencyId}")
  @Operation(
      summary = "Update a currency",
      description = "Updates a currency's code, name, symbol, and status")
  public ResponseEntity<CurrencyResponse> updateCurrency(
      @PathVariable String currencyId, @Valid @RequestBody UpdateCurrencyRequest request) {
    var updated = currencyService.updateCurrency(currencyId, request);
    return ResponseEntity.ok(updated);
  }
}
