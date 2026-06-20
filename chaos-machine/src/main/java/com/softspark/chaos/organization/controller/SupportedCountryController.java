package com.softspark.chaos.organization.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.organization.dto.CreateSupportedCountryRequest;
import com.softspark.chaos.organization.dto.SupportedCountryResponse;
import com.softspark.chaos.organization.service.SupportedCountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the supported-country subset used by the onboarding form.
 *
 * <p>Provides endpoints for marking a country supported, listing/getting the supported set, and
 * removing a membership.
 */
@RestController
@RequestMapping("/api/v0/supported-countries")
@Tag(name = "Supported Countries", description = "Curated countries available for onboarding")
public class SupportedCountryController {

  private final SupportedCountryService supportedCountryService;

  public SupportedCountryController(SupportedCountryService supportedCountryService) {
    this.supportedCountryService = supportedCountryService;
  }

  /**
   * Marks a country supported.
   *
   * @param request the creation request
   * @return the created supported-country membership
   */
  @PostMapping
  @Operation(
      summary = "Mark a country supported",
      description = "Adds a country to the curated subset shown on the onboarding form")
  public ResponseEntity<SupportedCountryResponse> createSupportedCountry(
      @Valid @RequestBody CreateSupportedCountryRequest request) {
    var created = supportedCountryService.createSupportedCountry(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Retrieves a supported-country membership by ID.
   *
   * @param supportedCountryId the membership ID
   * @return the membership
   */
  @GetMapping("/{supportedCountryId}")
  @Operation(
      summary = "Get a supported country",
      description = "Retrieves a supported-country membership by its ID")
  public ResponseEntity<SupportedCountryResponse> getSupportedCountry(
      @PathVariable String supportedCountryId) {
    var supported = supportedCountryService.getSupportedCountry(supportedCountryId);
    return ResponseEntity.ok(supported);
  }

  /**
   * Lists supported countries with pagination.
   *
   * @param page    the page number (0-indexed)
   * @param perPage the number of items per page
   * @return a paginated list of supported countries
   */
  @GetMapping
  @Operation(
      summary = "List supported countries",
      description = "Lists supported countries with their resolved country and primary currency")
  public ResponseEntity<PageResponse<SupportedCountryResponse>> listSupportedCountries(
      @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false)
          Integer page,
      @Parameter(description = "Number of items per page (max 100)") @RequestParam(required = false)
          Integer perPage,
      @Parameter(description = "Search term matched against country name, ISO code, or currency")
          @RequestParam(required = false)
          String search,
      @Parameter(description = "Sort field (country, isoCode, status, createdAt)")
          @RequestParam(required = false)
          String sortBy,
      @Parameter(description = "Sort direction (asc or desc)") @RequestParam(required = false)
          String sortDir) {
    var result =
        supportedCountryService.listSupportedCountries(page, perPage, search, sortBy, sortDir);
    return ResponseEntity.ok(result);
  }

  /**
   * Removes a supported-country membership.
   *
   * @param supportedCountryId the membership ID
   * @return a 204 No Content response
   */
  @DeleteMapping("/{supportedCountryId}")
  @Operation(
      summary = "Remove a supported country",
      description = "Removes a country from the curated onboarding subset")
  public ResponseEntity<Void> deleteSupportedCountry(@PathVariable String supportedCountryId) {
    supportedCountryService.deleteSupportedCountry(supportedCountryId);
    return ResponseEntity.noContent().build();
  }
}
